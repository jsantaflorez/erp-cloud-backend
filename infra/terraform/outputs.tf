output "backend_url" {
  description = "Cloud Run URL of the API (Firebase Hosting rewrites /api/** to this service)."
  value       = google_cloud_run_v2_service.backend.uri
}

output "sql_instance" {
  description = "Cloud SQL instance name (GitHub variable SQL_INSTANCE)."
  value       = google_sql_database_instance.main.name
}

output "sql_private_ip" {
  value = google_sql_database_instance.main.private_ip_address
}

output "sql_exports_bucket" {
  description = "Bucket with the daily exports (restore drill BUCKET)."
  value       = google_storage_bucket.sql_exports.name
}

output "artifact_repository" {
  description = "Docker repository (GitHub variable AR_REPOSITORY)."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}

output "wif_provider" {
  description = "GitHub variable WIF_PROVIDER."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "ci_service_account" {
  description = "GitHub variable CI_SERVICE_ACCOUNT."
  value       = google_service_account.ci.email
}
