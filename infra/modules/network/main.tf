# VPC: our own isolated network inside the region.
resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  # Instances get DNS names; ECS agent and AWS endpoints rely on name resolution.
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = var.name }
}

# Internet gateway: the door between the VPC and the internet. Free.
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = var.name }
}

# One public subnet per availability zone.
# for_each over a map {az => index}: the index gives each subnet its own slice of the VPC range.
resource "aws_subnet" "public" {
  for_each = { for index, az in var.azs : az => index }

  vpc_id            = aws_vpc.main.id
  availability_zone = each.key

  # Slice the /16 into /24 networks: 10.0.0.0/24, 10.0.1.0/24, ...
  cidr_block = cidrsubnet(var.vpc_cidr, 8, each.value)

  # Instances launched here get a public IPv4 automatically (no NAT Gateway, so they need it to reach the internet).
  map_public_ip_on_launch = true

  tags = { Name = "${var.name}-public-${each.key}" }
}

# Route table: rules for where traffic goes. Anything not inside the VPC goes to the internet gateway.
# A subnet with such a route is what makes it "public".
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.name}-public" }
}

# Attach the route table to each public subnet.
resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# Security group: the firewall of the ECS instance. Stateful: replies to allowed requests pass automatically.
resource "aws_security_group" "ecs_instance" {
  name        = "${var.name}-ecs-instance"
  description = "ECS container instance: web traffic in, everything out"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-ecs-instance" }
}

# Allow HTTP and HTTPS from anywhere (Caddy will listen on these ports). SSH is intentionally not opened.
resource "aws_vpc_security_group_ingress_rule" "web" {
  for_each = toset(["80", "443"])

  security_group_id = aws_security_group.ecs_instance.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
}

# Allow all outgoing traffic: pull images from ECR, talk to AWS APIs, Let's Encrypt.
resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.ecs_instance.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
