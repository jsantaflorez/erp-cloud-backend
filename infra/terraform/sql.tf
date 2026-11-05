# Cloud SQL for MySQL -- private IP only.
# BACKUP LAYER 1 lives here: automated daily backups + point-in-time recovery,
# plus protection against losing the backups together with the instance.
resource "google_sql_database_instance" "main" {
  name                = "erp-${var.env}-mysql"
  database_version    = var.db_version
  region              = var.region
  deletion_protection = true # Terraform refuses to destroy it

  settings {
    tier              = var.db_tier
    edition           = "ENTERPRISE" # shared-core tiers (db-f1-micro) are Enterprise only
    availability_type = "ZONAL"
    disk_type         = "PD_SSD"
    disk_size         = var.db_disk_gb
    disk_autoresize   = true
    user_labels       = local.labels

    deletion_protection_enabled = true # the Cloud SQL API itself refuses deletion
    retain_backups_on_delete    = true # backups survive an instance deletion

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.vpc.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }

    backup_configuration {
      enabled                        = true
      binary_log_enabled             = true    # MySQL point-in-time recovery
      start_time                     = "07:00" # UTC = 02:00 Bogota
      transaction_log_retention_days = var.pitr_days

      backup_retention_settings {
        retained_backups = var.backup_retained_count
        retention_unit   = "COUNT"
      }
    }

    # If the instance is ever deleted, Cloud SQL takes one last backup first.
    final_backup_config {
      enabled        = true
      retention_days = var.final_backup_retention_days
    }

    maintenance_window {
      day          = 7 # Sunday
      hour         = 8 # UTC = 03:00 Bogota
      update_track = "stable"
    }
  }

  depends_on = [google_service_networking_connection.psa]
}

resource "google_sql_database" "erpdb" {
  name      = "erpdb"
  instance  = google_sql_database_instance.main.name
  charset   = "utf8mb4"
  collation = "utf8mb4_0900_ai_ci"
}

# Two users (post-apply grants in README step 4):
#   erp_app     -> SELECT/INSERT/UPDATE/DELETE, used by the running service
#   erp_migrate -> schema changes, used only by the Flyway migration job
resource "random_password" "db_app" {
  length           = 32
  override_special = "-_.~"
}

resource "random_password" "db_migrate" {
  length           = 32
  override_special = "-_.~"
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "google_sql_user" "app" {
  name     = "erp_app"
  instance = google_sql_database_instance.main.name
  host     = "%"
  password = random_password.db_app.result
}

resource "google_sql_user" "migrate" {
  name     = "erp_migrate"
  instance = google_sql_database_instance.main.name
  host     = "%"
  password = random_password.db_migrate.result
}
