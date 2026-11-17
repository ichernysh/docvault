terraform {
  # use_lockfile for the S3 backend (used by later modules) needs Terraform 1.10+
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
