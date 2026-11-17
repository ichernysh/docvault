output "state_bucket_name" {
  description = "Bucket for Terraform state of later modules (used in their backend block)."
  value       = aws_s3_bucket.tfstate.id
}

output "ci_user_name" {
  description = "IAM user whose access key goes into GitHub Secrets."
  value       = aws_iam_user.ci_deploy.name
}
