variable "region" {
  description = "AWS region. eu-north-1 is the only region where this account allows EC2 operations."
  type        = string
  default     = "eu-north-1"
}

variable "account_id" {
  description = "AWS account id. The provider refuses to work with any other account (protects against a wrong profile)."
  type        = string
}

variable "state_bucket_name" {
  description = "Globally unique name of the S3 bucket that holds Terraform state of later modules."
  type        = string
}

variable "budget_email" {
  description = "Email that receives budget alerts."
  type        = string
}

variable "budget_limit_usd" {
  description = "Monthly budget limit in USD; alerts fire at 80% actual and 100% forecasted."
  type        = number
  default     = 5
}
