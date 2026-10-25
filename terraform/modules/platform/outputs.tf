output "load_balancer_ip" {
  value = google_compute_global_address.lb.address
}

output "frontend_bucket" {
  value = google_storage_bucket.frontend.name
}

output "cloud_run_service" {
  value = google_cloud_run_v2_service.backend.name
}
