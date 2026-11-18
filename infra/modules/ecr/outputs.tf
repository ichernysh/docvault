output "repository_urls" {
  description = "Repository URL per image name; this is what `docker push` and ECS task definitions use."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}
