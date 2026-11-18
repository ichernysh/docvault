variable "name" {
  description = "Prefix for Name tags of the network resources."
  type        = string
  default     = "docvault"
}

variable "vpc_cidr" {
  description = "IP range of the whole VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "Availability zones; one public subnet is created in each."
  type        = list(string)
}
