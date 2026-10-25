terraform {
  backend "gcs" {}
}

module "platform" {
  source = "../../modules/platform"

  project_id               = var.project_id
  region                   = var.region
  environment              = "prd"
  frontend_bucket_name     = var.frontend_bucket_name
  domain                   = var.domain
  backend_image            = var.backend_image
  admin_ip_ranges          = var.admin_ip_ranges
  db_tier                  = var.db_tier
  db_password              = var.db_password
  jwt_secret_name          = var.jwt_secret_name
  db_password_secret_name  = var.db_password_secret_name
  protect_database         = true
}
