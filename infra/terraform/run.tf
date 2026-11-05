locals {
  db_url = "jdbc:mysql://${google_sql_database_instance.main.private_ip_address}:3306/${google_sql_database.erpdb.name}?sslMode=REQUIRED&serverTimezone=UTC"

  # Plain environment shared by the service and both jobs.
  app_env = {
    SPRING_PROFILES_ACTIVE = "prod"
    DB_URL                 = local.db_url
    DB_POOL_SIZE           = "5"
    CORS_ALLOWED_ORIGINS   = var.cors_allowed_origins
    TRUST_FORWARDED_FOR    = "true"
    ADMIN_ROLES            = var.admin_roles
  }
}

# ---------------------------------------------------------------- API service
resource "google_cloud_run_v2_service" "backend" {
  name                 = "erp-backend"
  location             = var.region
  ingress              = "INGRESS_TRAFFIC_ALL"
  invoker_iam_disabled = true # public API (Firebase Hosting rewrites /api/** here); auth is the app's JWT
  deletion_protection  = true
  labels               = local.labels

  template {
    service_account                  = google_service_account.run.email
    max_instance_request_concurrency = 80
    timeout                          = "60s"

    scaling {
      min_instance_count = 0
      max_instance_count = 1 # REQUIRED: the login rate limit lives in memory
    }

    vpc_access {
      egress = "PRIVATE_RANGES_ONLY"
      network_interfaces {
        network    = google_compute_network.vpc.id
        subnetwork = google_compute_subnetwork.run_egress.id
      }
    }

    containers {
      image = var.backend_image

      ports {
        container_port = 8080
      }

      resources {
        limits            = { cpu = "1", memory = "1Gi" }
        cpu_idle          = true # billed per request
        startup_cpu_boost = true
      }

      dynamic "env" {
        for_each = merge(local.app_env, {
          DB_USERNAME    = google_sql_user.app.name
          FLYWAY_ENABLED = "false" # migrations run only in the migrate job
        })
        content {
          name  = env.key
          value = env.value
        }
      }

      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.this["DB_PASSWORD"].secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.this["JWT_SECRET"].secret_id
            version = "latest"
          }
        }
      }

      startup_probe {
        tcp_socket {
          port = 8080
        }
        period_seconds    = 5
        timeout_seconds   = 3
        failure_threshold = 24 # up to 2 minutes for a cold Spring Boot start
      }
    }
  }

  # CI deploys new images; Terraform must not roll them back.
  lifecycle {
    ignore_changes = [template[0].containers[0].image, client, client_version]
  }

  depends_on = [google_secret_manager_secret_iam_member.readers, google_secret_manager_secret_version.this]
}

# ---------------------------------------------------------------- Flyway migration job
# Same image, no web server: starts, runs Flyway with erp_migrate, validates and exits.
# CI runs it right after the pre-deploy backup and before updating the service.
resource "google_cloud_run_v2_job" "migrate" {
  name                = "erp-db-migrate"
  location            = var.region
  deletion_protection = false
  labels              = local.labels

  template {
    task_count = 1

    template {
      service_account = google_service_account.migrate.email
      max_retries     = 0
      timeout         = "900s"

      vpc_access {
        egress = "PRIVATE_RANGES_ONLY"
        network_interfaces {
          network    = google_compute_network.vpc.id
          subnetwork = google_compute_subnetwork.run_egress.id
        }
      }

      containers {
        image = var.backend_image
        args  = ["--spring.main.web-application-type=none"]

        resources {
          limits = { cpu = "1", memory = "1Gi" }
        }

        dynamic "env" {
          for_each = merge(local.app_env, {
            DB_USERNAME    = google_sql_user.migrate.name
            FLYWAY_ENABLED = "true"
          })
          content {
            name  = env.key
            value = env.value
          }
        }

        env {
          name = "DB_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.this["DB_MIGRATE_PASSWORD"].secret_id
              version = "latest"
            }
          }
        }

        env {
          name = "JWT_SECRET"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.this["JWT_SECRET"].secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].template[0].containers[0].image, client, client_version]
  }

  depends_on = [google_secret_manager_secret_iam_member.readers, google_secret_manager_secret_version.this]
}

# ---------------------------------------------------------------- restore check job (layer 4)
# Used by scripts/backup/restore_drill.sh: the drill overrides DB_URL with the
# restored copy's IP. Success = the app starts and Hibernate validates the schema
# against the restored database, i.e. the backup is really usable.
resource "google_cloud_run_v2_job" "restore_check" {
  name                = "erp-restore-check"
  location            = var.region
  deletion_protection = false
  labels              = local.labels

  template {
    task_count = 1

    template {
      service_account = google_service_account.run.email
      max_retries     = 0
      timeout         = "600s"

      vpc_access {
        egress = "PRIVATE_RANGES_ONLY"
        network_interfaces {
          network    = google_compute_network.vpc.id
          subnetwork = google_compute_subnetwork.run_egress.id
        }
      }

      containers {
        image = var.backend_image
        args  = ["--spring.main.web-application-type=none"]

        resources {
          limits = { cpu = "1", memory = "1Gi" }
        }

        dynamic "env" {
          for_each = merge(local.app_env, {
            DB_URL         = "set-by-restore-drill"
            DB_USERNAME    = google_sql_user.app.name
            FLYWAY_ENABLED = "false"
          })
          content {
            name  = env.key
            value = env.value
          }
        }

        env {
          name = "DB_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.this["DB_PASSWORD"].secret_id
              version = "latest"
            }
          }
        }

        env {
          name = "JWT_SECRET"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.this["JWT_SECRET"].secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].template[0].containers[0].image, client, client_version]
  }

  depends_on = [google_secret_manager_secret_iam_member.readers, google_secret_manager_secret_version.this]
}
