# One service account per job, each with only what it needs.
resource "google_service_account" "run" {
  account_id   = "erp-backend-run"
  display_name = "ERP backend (Cloud Run service + restore check job)"
}

resource "google_service_account" "migrate" {
  account_id   = "erp-db-migrate"
  display_name = "ERP Flyway migration job"
}

resource "google_service_account" "ci" {
  account_id   = "erp-ci-deploy"
  display_name = "ERP GitHub Actions deploy"
}

resource "google_service_account" "backup" {
  account_id   = "erp-sql-export"
  display_name = "ERP daily SQL export (workflow + scheduler)"
}

# ---------------------------------------------------------------- secrets
locals {
  secret_readers = {
    run_db_password     = { sa = google_service_account.run.email, secret = "DB_PASSWORD" }
    run_jwt             = { sa = google_service_account.run.email, secret = "JWT_SECRET" }
    migrate_db_password = { sa = google_service_account.migrate.email, secret = "DB_MIGRATE_PASSWORD" }
    migrate_jwt         = { sa = google_service_account.migrate.email, secret = "JWT_SECRET" } # the app refuses to start without it
  }
}

resource "google_secret_manager_secret_iam_member" "readers" {
  for_each  = local.secret_readers
  secret_id = google_secret_manager_secret.this[each.value.secret].id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${each.value.sa}"
}

# ---------------------------------------------------------------- CI (deploy pipeline)
# BACKUP LAYER 2: CI takes an on-demand backup before every migration/deploy.
resource "google_project_iam_custom_role" "pre_deploy_backup" {
  role_id     = "erpPreDeployBackup"
  title       = "ERP pre-deploy SQL backup"
  description = "Create and watch on-demand Cloud SQL backups from the deploy pipeline."
  permissions = [
    "cloudsql.backupRuns.create",
    "cloudsql.backupRuns.get",
    "cloudsql.backupRuns.list",
    "cloudsql.instances.get",
  ]
}

resource "google_project_iam_member" "ci" {
  for_each = toset([
    "roles/run.developer",
    google_project_iam_custom_role.pre_deploy_backup.name,
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.ci.email}"
}

resource "google_service_account_iam_member" "ci_acts_as" {
  for_each           = { run = google_service_account.run.name, migrate = google_service_account.migrate.name }
  service_account_id = each.value
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.ci.email}"
}

resource "google_artifact_registry_repository_iam_member" "ci_push" {
  location   = google_artifact_registry_repository.images.location
  repository = google_artifact_registry_repository.images.name
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.ci.email}"
}

# ---------------------------------------------------------------- daily export (layer 3)
resource "google_project_iam_custom_role" "sql_export" {
  role_id     = "erpSqlExport"
  title       = "ERP daily SQL export"
  description = "Start a Cloud SQL export to Cloud Storage and watch the operation."
  permissions = [
    "cloudsql.instances.export",
    "cloudsql.instances.get",
  ]
}

resource "google_project_iam_member" "backup" {
  for_each = toset([
    google_project_iam_custom_role.sql_export.name,
    "roles/logging.logWriter",
    "roles/workflows.invoker",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.backup.email}"
}
