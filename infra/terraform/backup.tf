# =============================================================================
# Backup and recovery strategy (Claude Doc "Estrategia de backup y recuperacion")
#   Layer 1  Automated backups + PITR ................ sql.tf
#   Layer 2  On-demand backup before every deploy .... iam.tf (role) + .github/workflows/deploy-backend.yml
#   Layer 3  Daily export to Cloud Storage ........... this file
#   Layer 4  Monthly restore drill ................... run.tf (erp-restore-check) + scripts/backup/restore_drill.sh
#   Alerts   Email on export / scheduler / SQL failures  this file
# =============================================================================

# ---------------------------------------------------------------- layer 3: bucket
# Lives outside the instance (survives deleting it or its region), objects
# cannot be deleted or overwritten for export_retention_days, then expire.
resource "google_storage_bucket" "sql_exports" {
  name                        = "${var.project_id}-erp-sql-exports"
  location                    = var.export_bucket_location
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false
  labels                      = local.labels

  retention_policy {
    retention_period = tostring(var.export_retention_days * 86400)
    # Lock it (irreversible) once the first month of exports looks right:
    #   gcloud storage buckets update gs://<bucket> --lock-retention-period
    is_locked = false
  }

  lifecycle_rule {
    condition {
      age = var.export_retention_days + 1
    }
    action {
      type = "Delete"
    }
  }

  soft_delete_policy {
    retention_duration_seconds = 7 * 86400
  }

  depends_on = [google_project_service.apis]
}

# The Cloud SQL instance writes the export file (and reads it back in the drill).
resource "google_storage_bucket_iam_member" "sql_instance_writer" {
  bucket = google_storage_bucket.sql_exports.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_sql_database_instance.main.service_account_email_address}"
}

# ---------------------------------------------------------------- layer 3: workflow + schedule
resource "google_workflows_workflow" "sql_export" {
  name                = "erp-sql-export"
  region              = var.region
  description         = "Daily export of erpdb to gs://${google_storage_bucket.sql_exports.name}/daily/"
  service_account     = google_service_account.backup.email
  call_log_level      = "LOG_ERRORS_ONLY"
  deletion_protection = false
  labels              = local.labels

  source_contents = templatefile("${path.module}/workflows/sql-export.yaml", {
    bucket   = google_storage_bucket.sql_exports.name
    database = google_sql_database.erpdb.name
    instance = google_sql_database_instance.main.name
  })

  depends_on = [google_project_iam_member.backup]
}

resource "google_cloud_scheduler_job" "sql_export_daily" {
  name        = "erp-sql-export-daily"
  region      = var.region
  description = "Runs the erp-sql-export workflow every day."
  schedule    = var.export_cron
  time_zone   = var.export_time_zone

  retry_config {
    retry_count = 1
  }

  http_target {
    http_method = "POST"
    uri         = "https://workflowexecutions.googleapis.com/v1/${google_workflows_workflow.sql_export.id}/executions"
    body        = base64encode("{}")
    headers     = { "Content-Type" = "application/json" }

    oauth_token {
      service_account_email = google_service_account.backup.email
      scope                 = "https://www.googleapis.com/auth/cloud-platform"
    }
  }

  depends_on = [google_project_service.apis]
}

# ---------------------------------------------------------------- alerts
resource "google_monitoring_notification_channel" "email" {
  display_name = "ERP alertas (${var.alert_email})"
  type         = "email"
  labels = {
    email_address = var.alert_email
  }
  depends_on = [google_project_service.apis]
}

# The daily export workflow failed (export error, permissions, instance busy...).
resource "google_monitoring_alert_policy" "export_failed" {
  display_name          = "ERP: fallo el export diario de la base"
  combiner              = "OR"
  notification_channels = [google_monitoring_notification_channel.email.id]

  conditions {
    display_name = "Ejecucion fallida de erp-sql-export"
    condition_threshold {
      filter          = "resource.type = \"workflows.googleapis.com/Workflow\" AND resource.label.workflow_id = \"${google_workflows_workflow.sql_export.name}\" AND metric.type = \"workflows.googleapis.com/finished_execution_count\" AND metric.label.status = \"FAILED\""
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      duration        = "0s"
      aggregations {
        alignment_period   = "300s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  documentation {
    content   = "El export diario de erpdb a Cloud Storage fallo. Revisar las ejecuciones del workflow erp-sql-export y lanzarlo a mano cuando se corrija."
    mime_type = "text/markdown"
  }
}

# The scheduler could not start the workflow (so no export ran at all).
resource "google_monitoring_alert_policy" "scheduler_failed" {
  display_name          = "ERP: el scheduler no pudo lanzar el export"
  combiner              = "OR"
  notification_channels = [google_monitoring_notification_channel.email.id]

  conditions {
    display_name = "Error en erp-sql-export-daily"
    condition_matched_log {
      filter = "resource.type=\"cloud_scheduler_job\" AND resource.labels.job_id=\"${google_cloud_scheduler_job.sql_export_daily.name}\" AND severity>=ERROR"
    }
  }

  alert_strategy {
    notification_rate_limit {
      period = "3600s"
    }
  }
}

# Any failed Cloud SQL operation recorded in the audit log (includes failed
# automated or on-demand backups).
resource "google_monitoring_alert_policy" "sql_operation_failed" {
  display_name          = "ERP: operacion fallida en Cloud SQL (incluye backups)"
  combiner              = "OR"
  notification_channels = [google_monitoring_notification_channel.email.id]

  conditions {
    display_name = "Error en ${google_sql_database_instance.main.name}"
    condition_matched_log {
      filter = "resource.type=\"cloudsql_database\" AND resource.labels.database_id=\"${var.project_id}:${google_sql_database_instance.main.name}\" AND logName:\"cloudaudit.googleapis.com\" AND severity>=ERROR"
    }
  }

  alert_strategy {
    notification_rate_limit {
      period = "3600s"
    }
  }
}
