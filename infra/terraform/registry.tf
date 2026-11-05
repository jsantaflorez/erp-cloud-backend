resource "google_artifact_registry_repository" "images" {
  repository_id = "erp"
  location      = var.region
  format        = "DOCKER"
  description   = "ERP backend images"
  labels        = local.labels

  # Keep the 2 most recent images (current + rollback), delete the rest after a day.
  cleanup_policies {
    id     = "keep-last-2"
    action = "KEEP"
    most_recent_versions {
      keep_count = 2
    }
  }

  cleanup_policies {
    id     = "delete-older"
    action = "DELETE"
    condition {
      tag_state  = "ANY"
      older_than = "86400s"
    }
  }

  depends_on = [google_project_service.apis]
}
