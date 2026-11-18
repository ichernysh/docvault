variable "name" {
  description = "Name of the ECS cluster and prefix for related resources."
  type        = string
  default     = "docvault"
}

variable "subnet_ids" {
  description = "Subnets where the ASG may launch the container instance."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group attached to the container instance."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type of the container instance."
  type        = string
  default     = "t3.medium"
}

variable "desired_capacity" {
  description = "Number of container instances: 0 = off (costs nothing), 1 = running. Maximum is 1."
  type        = number
  default     = 0

  validation {
    condition     = contains([0, 1], var.desired_capacity)
    error_message = "desired_capacity must be 0 or 1 (single-instance budget)."
  }
}
