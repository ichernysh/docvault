variable "region" {
  description = "AWS region. eu-north-1 is the only region where this account allows EC2 operations."
  type        = string
  default     = "eu-north-1"
}

variable "account_id" {
  description = "AWS account id. The provider refuses to work with any other account."
  type        = string
}
