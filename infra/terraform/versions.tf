terraform {
  required_version = ">= 1.6"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.10"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State in a private GCS bucket created by hand once (see README, step 1):
  #   terraform init -backend-config="bucket=<PROJECT_ID>-tfstate" -backend-config="prefix=erp-cloud/piloto"
  backend "gcs" {}
}

provider "google" {
  project = var.project_id
  region  = var.region

  # Needed by the Billing Budgets API (budget.tf).
  user_project_override = true
  billing_project       = var.project_id
}
