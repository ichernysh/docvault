output "vpc_id" {
  description = "Id of the VPC."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Ids of the public subnets, one per availability zone."
  value       = [for subnet in aws_subnet.public : subnet.id]
}

output "ecs_instance_sg_id" {
  description = "Security group for the ECS container instance."
  value       = aws_security_group.ecs_instance.id
}
