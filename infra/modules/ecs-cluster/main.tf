# 1. The ECS cluster itself: a logical group that container instances join and where tasks run.
resource "aws_ecs_cluster" "main" {
  name = var.name

  # Container Insights is billed per metric; keep it off.
  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

# 2. IAM role of the container instance (EC2 server).
# The trust policy says: the EC2 service may assume this role.
data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_instance" {
  name               = "${var.name}-ecs-instance"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

# Rights the ECS agent on the server needs: register in the cluster, pull images from ECR, send logs.
resource "aws_iam_role_policy_attachment" "ecs_instance" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

# Lets us open a shell on the server through AWS Systems Manager, so SSH port stays closed.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# EC2 cannot use a role directly; it uses an instance profile that wraps the role.
resource "aws_iam_instance_profile" "ecs_instance" {
  name = "${var.name}-ecs-instance"
  role = aws_iam_role.ecs_instance.name
}

# 3. AMI (server disk image): AWS publishes the id of the latest ECS-optimized image in SSM.
# It already contains Docker and the ECS agent. We read it instead of hardcoding an id that gets outdated.
data "aws_ssm_parameter" "ecs_ami" {
  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}

# 4. Launch template: the recipe for every server the ASG starts.
resource "aws_launch_template" "ecs_instance" {
  name_prefix   = "${var.name}-ecs-"
  image_id      = data.aws_ssm_parameter.ecs_ami.value
  instance_type = var.instance_type

  iam_instance_profile {
    arn = aws_iam_instance_profile.ecs_instance.arn
  }

  # Public IP is required: there is no NAT Gateway, the server reaches ECR and the internet directly.
  network_interfaces {
    associate_public_ip_address = true
    security_groups             = [var.security_group_id]
  }

  # IMDSv2 only (token required). Hop limit 1 keeps containers in bridge mode away from the
  # instance role credentials: only the host (ECS agent) can read them.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  # On boot, tell the ECS agent which cluster to join.
  user_data = base64encode(<<-EOT
    #!/bin/bash
    echo "ECS_CLUSTER=${aws_ecs_cluster.main.name}" >> /etc/ecs/ecs.config
  EOT
  )

  # Provider default_tags do not reach instances started by the ASG, so tag them here.
  tag_specifications {
    resource_type = "instance"

    tags = {
      Name    = "${var.name}-ecs-instance"
      project = "docvault"
    }
  }
}

# 5. Auto Scaling Group: keeps the number of servers equal to desired_capacity.
# 0 = no server, no cost. 1 = one server running. max_size 1 protects the budget.
resource "aws_autoscaling_group" "ecs_instance" {
  name                = "${var.name}-ecs-instance"
  min_size            = 0
  max_size            = 1
  desired_capacity    = var.desired_capacity
  vpc_zone_identifier = var.subnet_ids

  launch_template {
    id      = aws_launch_template.ecs_instance.id
    version = "$Latest"
  }

  # AWS adds this tag itself when the ASG becomes a capacity provider.
  # Declaring it keeps the code equal to reality (otherwise every plan wants to remove it).
  tag {
    key                 = "AmazonECSManaged"
    value               = ""
    propagate_at_launch = true
  }
}

# 6. Capacity provider: connects the cluster to the ASG so ECS knows where it may place tasks.
# Managed scaling is off: the server count is controlled only by desired_capacity.
resource "aws_ecs_capacity_provider" "ec2" {
  name = "${var.name}-ec2"

  auto_scaling_group_provider {
    auto_scaling_group_arn = aws_autoscaling_group.ecs_instance.arn

    managed_scaling {
      status = "DISABLED"
    }
  }
}

# Attach the capacity provider to the cluster and make it the default for new tasks.
resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = [aws_ecs_capacity_provider.ec2.name]

  default_capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    weight            = 1
  }
}
