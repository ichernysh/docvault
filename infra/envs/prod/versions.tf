terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # State lives in the S3 bucket created by infra/bootstrap.
  # The bucket name is NOT written here (it contains the account id): it comes from backend.hcl,
  # passed with `terraform init -backend-config=backend.hcl`.
  backend "s3" {
    # One state file per environment inside the shared bucket.
    key    = "prod/terraform.tfstate"
    region = "eu-north-1"

    # Locking with a lock file next to the state (needs Terraform 1.10+), no DynamoDB table.
    use_lockfile = true
  }
}
