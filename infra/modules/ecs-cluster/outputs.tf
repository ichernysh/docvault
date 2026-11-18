output "cluster_name" {
  description = "ECS cluster name (used by services and deploy workflows)."
  value       = aws_ecs_cluster.main.name
}

output "cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.main.arn
}

output "asg_name" {
  description = "Auto Scaling Group name; cloud-up/cloud-down change its desired capacity."
  value       = aws_autoscaling_group.ecs_instance.name
}
