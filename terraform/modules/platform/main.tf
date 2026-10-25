terraform {
  required_version = ">= 1.8.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 6.0, < 8.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

locals {
  services = toset([
    "compute.googleapis.com",
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "servicenetworking.googleapis.com",
    "storage.googleapis.com",
    "secretmanager.googleapis.com",
  ])
  admin_allowlist = join(" || ", [
    for cidr in var.admin_ip_ranges : "inIpRange(origin.ip, '${cidr}')"
  ])
}

resource "google_project_service" "enabled" {
  for_each           = local.services
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_compute_network" "main" {
  name                    = "${var.environment}-network"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "run" {
  name                     = "${var.environment}-run"
  region                   = var.region
  network                  = google_compute_network.main.id
  ip_cidr_range             = "10.10.0.0/24"
  private_ip_google_access = true
}

resource "google_compute_global_address" "private_services" {
  name          = "${var.environment}-private-services"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.main.id
}

resource "google_service_networking_connection" "private_services" {
  network                 = google_compute_network.main.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_services.name]
  depends_on              = [google_project_service.enabled]
}

resource "google_sql_database_instance" "mysql" {
  name                = "${var.environment}-mysql"
  region              = var.region
  database_version    = "MYSQL_8_4"
  deletion_protection = var.protect_database

  settings {
    tier = var.db_tier

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.main.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
    }
  }

  depends_on = [google_service_networking_connection.private_services]
}

resource "google_sql_database" "app" {
  name     = "app_db"
  instance = google_sql_database_instance.mysql.name
}

resource "google_sql_user" "app" {
  name     = "app"
  instance = google_sql_database_instance.mysql.name
  password = var.db_password
}

resource "google_service_account" "backend" {
  account_id   = "${var.environment}-backend"
  display_name = "${var.environment} backend service"
}

resource "google_secret_manager_secret_iam_member" "backend_secrets" {
  for_each = toset([
    var.jwt_secret_name,
    var.db_password_secret_name,
    var.mail_password_secret_name,
  ])

  project   = var.project_id
  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.backend.email}"
}

resource "google_cloud_run_v2_service" "backend" {
  name                = "${var.environment}-backend"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"
  deletion_protection = var.protect_database

  template {
    service_account = google_service_account.backend.email

    vpc_access {
      egress = "PRIVATE_RANGES_ONLY"
      network_interfaces {
        network    = google_compute_network.main.id
        subnetwork = google_compute_subnetwork.run.id
      }
    }

    containers {
      image = var.backend_image

      ports {
        container_port = 8080
      }

      env {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:mysql://${google_sql_database_instance.mysql.private_ip_address}:3306/${google_sql_database.app.name}?sslMode=REQUIRED"
      }
      env {
        name  = "SPRING_DATASOURCE_USERNAME"
        value = google_sql_user.app.name
      }
      env {
        name = "SPRING_DATASOURCE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = var.db_password_secret_name
            version = "latest"
          }
        }
      }
      env {
        name = "APP_JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = var.jwt_secret_name
            version = "latest"
          }
        }
        env {
          name  = "SPRING_MAIL_HOST"
          value = var.mail_host
        }
        env {
          name  = "SPRING_MAIL_PORT"
          value = "587"
        }
        env {
          name  = "SPRING_MAIL_USERNAME"
          value = var.mail_username
        }
        env {
          name = "SPRING_MAIL_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = var.mail_password_secret_name
              version = "latest"
            }
          }
        }
        env {
          name  = "SPRING_MAIL_AUTH"
          value = "true"
        }
        env {
          name  = "SPRING_MAIL_STARTTLS"
          value = "true"
        }
        env {
          name  = "APP_MAIL_FROM"
          value = var.mail_from
        }
      }
    }
  }

  depends_on = [
    google_project_service.enabled,
    google_secret_manager_secret_iam_member.backend_secrets,
    google_sql_database.app,
    google_sql_user.app,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "load_balancer" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.backend.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_compute_region_network_endpoint_group" "backend" {
  name                  = "${var.environment}-backend-neg"
  region                = var.region
  network_endpoint_type = "SERVERLESS"

  cloud_run {
    service = google_cloud_run_v2_service.backend.name
  }
}

resource "google_compute_security_policy" "api" {
  name = "${var.environment}-api-policy"

  rule {
    action      = "deny(403)"
    priority    = 1000
    description = "Restrict administrative API paths to trusted address ranges"
    match {
      expr {
        expression = "request.path.startsWith('/api/admin/') && !(${local.admin_allowlist})"
      }
    }
  }

  rule {
    action      = "throttle"
    priority    = 2000
    description = "Limit API requests per client IP"
    match {
      expr {
        expression = "request.path.startsWith('/api/')"
      }
    }
    rate_limit_options {
      conform_action = "allow"
      exceed_action  = "deny(429)"
      enforce_on_key = "IP"
      rate_limit_threshold {
        count        = 100
        interval_sec = 60
      }
    }
  }

  rule {
    action   = "allow"
    priority = 2147483647
    match {
      versioned_expr = "SRC_IPS_V1"
      config {
        src_ip_ranges = ["*"]
      }
    }
  }
}

resource "google_compute_backend_service" "api" {
  name                  = "${var.environment}-api"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  protocol              = "HTTP"
  security_policy       = google_compute_security_policy.api.id

  backend {
    group = google_compute_region_network_endpoint_group.backend.id
  }
}

resource "google_storage_bucket" "frontend" {
  name                        = var.frontend_bucket_name
  location                    = var.region
  uniform_bucket_level_access = true

  website {
    main_page_suffix = "index.html"
    not_found_page   = "index.html"
  }
}

resource "google_storage_bucket_iam_member" "frontend_public" {
  bucket = google_storage_bucket.frontend.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

resource "google_compute_backend_bucket" "frontend" {
  name        = "${var.environment}-frontend"
  bucket_name = google_storage_bucket.frontend.name
  enable_cdn  = true
}

resource "google_compute_url_map" "main" {
  name            = "${var.environment}-url-map"
  default_service = google_compute_backend_bucket.frontend.id

  host_rule {
    hosts        = [var.domain]
    path_matcher = "application"
  }

  path_matcher {
    name            = "application"
    default_service = google_compute_backend_bucket.frontend.id

    path_rule {
      paths   = ["/api", "/api/*"]
      service = google_compute_backend_service.api.id
    }
  }
}

resource "google_compute_managed_ssl_certificate" "main" {
  name = "${var.environment}-certificate"
  managed {
    domains = [var.domain]
  }
}

resource "google_compute_target_https_proxy" "main" {
  name             = "${var.environment}-https-proxy"
  url_map          = google_compute_url_map.main.id
  ssl_certificates = [google_compute_managed_ssl_certificate.main.id]
}

resource "google_compute_global_address" "lb" {
  name = "${var.environment}-lb-ip"
}

resource "google_compute_global_forwarding_rule" "https" {
  name                  = "${var.environment}-https"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  ip_address            = google_compute_global_address.lb.address
  port_range            = "443"
  target                = google_compute_target_https_proxy.main.id
}
