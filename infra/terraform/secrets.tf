# Secrets are generated here and read by Cloud Run at runtime; never in git.
# They are also stored in the Terraform state, which is why the state bucket
# must stay private (README step 1).
locals {
  secrets = {
    DB_PASSWORD         = random_password.db_app.result
    DB_MIGRATE_PASSWORD = random_password.db_migrate.result
    JWT_SECRET          = random_password.jwt.result
  }
}

resource "google_secret_manager_secret" "this" {
  for_each  = local.secrets
  secret_id = each.key
  labels    = local.labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "this" {
  for_each    = local.secrets
  secret      = google_secret_manager_secret.this[each.key].id
  secret_data = each.value
}
