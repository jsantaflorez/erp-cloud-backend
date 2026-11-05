variable "project_id" {
  description = "Google Cloud project id of the pilot."
  type        = string
}

variable "region" {
  description = "Region for Cloud Run, Cloud SQL and the scheduler (decision pending: us-east1 recommended, northamerica-south1 alternative)."
  type        = string
  default     = "us-east1"
}

variable "env" {
  description = "Environment name, used in resource names and labels."
  type        = string
  default     = "piloto"
}

variable "billing_account" {
  description = "Billing account id (XXXXXX-XXXXXX-XXXXXX) for the budget."
  type        = string
}

variable "alert_email" {
  description = "Email that receives backup, export and budget alerts."
  type        = string
}

variable "github_repository" {
  description = "owner/repo allowed to deploy through Workload Identity Federation."
  type        = string
  default     = "jsantaflorez/erp-cloud-backend"
}

variable "backend_image" {
  description = "Initial container image. CI replaces it on every deploy (Terraform ignores later changes)."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}

variable "cors_allowed_origins" {
  description = "Allowed frontend origins (comma separated). With Firebase rewrites the API is same-origin, but keep the real domain here."
  type        = string
  default     = ""
}

variable "admin_roles" {
  description = "Role codes treated as administrators by the backend (app.security.admin-roles)."
  type        = string
  default     = "SYS_ADMIN,COMPANY_ADMIN"
}

# ---------------------------------------------------------------- database
variable "db_version" {
  description = "Cloud SQL MySQL version. Must match the local server (SELECT VERSION();): MYSQL_8_0 or MYSQL_8_4."
  type        = string
  default     = "MYSQL_8_0"
}

variable "db_tier" {
  description = "Cloud SQL machine tier. Move to db-g1-small if CPU stays above 70 %."
  type        = string
  default     = "db-f1-micro"
}

variable "db_disk_gb" {
  description = "Initial SSD size in GB (auto-grows)."
  type        = number
  default     = 10
}

# ---------------------------------------------------------------- backup (layers 1-4)
variable "backup_retained_count" {
  description = "Layer 1: number of automated daily backups kept."
  type        = number
  default     = 14
}

variable "pitr_days" {
  description = "Layer 1: days of binary logs for point-in-time recovery (max 7 on Enterprise edition)."
  type        = number
  default     = 7
}

variable "final_backup_retention_days" {
  description = "Days a final backup is kept if the instance is ever deleted."
  type        = number
  default     = 30
}

variable "export_retention_days" {
  description = "Layer 3: days each daily export is kept (retention policy + lifecycle)."
  type        = number
  default     = 30
}

variable "export_bucket_location" {
  description = "Layer 3: export bucket location. Multi-region US survives a regional outage."
  type        = string
  default     = "US"
}

variable "export_cron" {
  description = "Layer 3: daily export schedule. 03:00 so it never overlaps the 02:00 automated backup (Cloud SQL runs one operation at a time)."
  type        = string
  default     = "0 3 * * *"
}

variable "export_time_zone" {
  description = "Time zone of export_cron."
  type        = string
  default     = "America/Bogota"
}

# ---------------------------------------------------------------- budget
variable "budget_usd" {
  description = "Monthly budget in USD. Credits are excluded so alerts reflect real spend."
  type        = number
  default     = 15
}
