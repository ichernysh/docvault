# The provider is the plugin that talks to AWS. Every resource below goes through it.
provider "aws" {
  # Region for all resources. Comes from a variable so it is defined in one place.
  region = var.region

  # Terraform refuses to run if the credentials belong to another account.
  # Protects against a wrong AWS_PROFILE (this machine used to have keys of a different account).
  allowed_account_ids = [var.account_id]

  # Tags added automatically to every resource that supports tags (project rule: project = "docvault").
  default_tags {
    tags = {
      project = "docvault"
    }
  }
}

# Next, step by step (apply after each part):
#   1. S3 bucket for state
#   2. budget with email alert
#   3. IAM user ci-deploy

# --- 1. S3 bucket for Terraform state of later modules ---

# The bucket itself. Name must be globally unique across all AWS accounts.
resource "aws_s3_bucket" "tfstate" {
  bucket = var.state_bucket_name

  # Safety net: `terraform destroy` refuses to delete the bucket that holds all state.
  lifecycle {
    prevent_destroy = true
  }
}

# Keep old versions of the state file, so a broken state can be rolled back.
resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Forbid any public access, even if someone later adds a public ACL or policy by mistake.
resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# --- 2. Budget with email alerts ---

# Monthly cost budget. AWS sends an email when spending crosses the thresholds below.
resource "aws_budgets_budget" "monthly" {
  name         = "docvault-monthly"
  budget_type  = "COST"
  time_unit    = "MONTHLY"
  limit_amount = tostring(var.budget_limit_usd)
  limit_unit   = "USD"

  # Alert when 80% of the limit is actually spent.
  notification {
    comparison_operator        = "GREATER_THAN"
    notification_type          = "ACTUAL"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    subscriber_email_addresses = [var.budget_email]
  }

  # Alert when AWS forecasts that the limit will be exceeded by the end of the month.
  notification {
    comparison_operator        = "GREATER_THAN"
    notification_type          = "FORECASTED"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    subscriber_email_addresses = [var.budget_email]
  }
}
