terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0, < 7.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = var.tags
  }
}

data "aws_partition" "current" {}

locals {
  image_uri = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
  s3_prefix = trim(var.import_s3_prefix, "/")
  s3_objects = local.s3_prefix == "" ? "arn:${data.aws_partition.current.partition}:s3:::${var.import_s3_bucket_name}/*" : "arn:${data.aws_partition.current.partition}:s3:::${var.import_s3_bucket_name}/${local.s3_prefix}/*"
}

resource "aws_ecr_repository" "app" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Retain the newest 20 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.service_name}"
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role" "execution" {
  name                 = "${var.service_name}-execution"
  permissions_boundary = var.permissions_boundary_arn
  assume_role_policy   = local.ecs_task_assume_role
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "task" {
  name                 = "${var.service_name}-task"
  permissions_boundary = var.permissions_boundary_arn
  assume_role_policy   = local.ecs_task_assume_role
}

locals {
  ecs_task_assume_role = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "task" {
  name = "semantic-search-dependencies"
  role = aws_iam_role.task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadSemanticImports"
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = local.s3_objects
      },
      {
        Sid    = "InvokeEmbeddingModel"
        Effect = "Allow"
        Action = ["bedrock:InvokeModel"]
        Resource = [
          "arn:${data.aws_partition.current.partition}:bedrock:${var.aws_region}::foundation-model/${var.bedrock_model_id}"
        ]
      }
    ]
  })
}

resource "aws_security_group" "alb" {
  name_prefix = "${var.service_name}-alb-"
  description = "Ingress to the internal semantic-search ALB"
  vpc_id      = var.vpc_id

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_security_groups" {
  for_each = toset(var.client_security_group_ids)

  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = var.load_balancer_port
  to_port                      = var.load_balancer_port
  description                  = "Semantic search client security group"
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cidrs" {
  for_each = toset(var.client_cidr_blocks)

  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = var.load_balancer_port
  to_port           = var.load_balancer_port
  description       = "Semantic search client CIDR"
}

resource "aws_security_group" "task" {
  name_prefix = "${var.service_name}-task-"
  description = "Semantic-search ECS tasks"
  vpc_id      = var.vpc_id

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_egress_rule" "alb_to_task" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.task.id
  ip_protocol                  = "tcp"
  from_port                    = var.container_port
  to_port                      = var.container_port
  description                  = "Forward requests to semantic-search tasks"
}

resource "aws_vpc_security_group_ingress_rule" "task_from_alb" {
  security_group_id            = aws_security_group.task.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = var.container_port
  to_port                      = var.container_port
  description                  = "Requests from the semantic-search ALB"
}

resource "aws_vpc_security_group_egress_rule" "task_dependencies" {
  security_group_id = aws_security_group.task.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "Elasticsearch, S3, Bedrock, ECR, and CloudWatch dependencies"
}

resource "aws_lb" "app" {
  name               = substr(var.service_name, 0, 32)
  internal           = true
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.subnet_ids
}

resource "aws_lb_target_group" "app" {
  name_prefix          = "sem-"
  port                 = var.container_port
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = var.vpc_id
  deregistration_delay = 30

  health_check {
    enabled             = true
    path                = "/version"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = var.load_balancer_port
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_ecs_cluster" "app" {
  name = var.service_name

  setting {
    name  = "containerInsights"
    value = var.container_insights_enabled ? "enabled" : "disabled"
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = var.service_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.task_cpu)
  memory                   = tostring(var.task_memory_mb)
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  ephemeral_storage {
    size_in_gib = var.ephemeral_storage_gib
  }

  container_definitions = jsonencode([{
    name      = "semantic-search"
    image     = local.image_uri
    essential = true
    portMappings = [{
      name          = "http"
      containerPort = var.container_port
      hostPort      = var.container_port
      protocol      = "tcp"
    }]
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "ELASTICSEARCH_URL", value = var.elasticsearch_url },
      { name = "BEDROCK_MODEL_ID", value = var.bedrock_model_id },
      { name = "SEMANTIC_INDEX_ALIAS", value = var.semantic_index_alias },
      { name = "IMPORT_CONTROL_INDEX", value = var.import_control_index },
      { name = "EMBEDDING_CONCURRENCY", value = tostring(var.embedding_concurrency) },
      { name = "ELASTICSEARCH_BATCH_SIZE", value = tostring(var.elasticsearch_batch_size) },
      { name = "BEDROCK_MAX_ATTEMPTS", value = tostring(var.bedrock_max_attempts) }
    ]
    # The non-root application creates import files in /tmp. An anonymous Fargate
    # volume is root-owned, so use the task's ephemeral container filesystem.
    readonlyRootFilesystem = false
    mountPoints            = []
    volumesFrom = []
    linuxParameters = {
      initProcessEnabled = true
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "app"
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  name            = var.service_name
  cluster         = aws_ecs_cluster.app.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100
  health_check_grace_period_seconds  = 60
  enable_execute_command             = false

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = concat([aws_security_group.task.id], var.additional_task_security_group_ids)
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "semantic-search"
    container_port   = var.container_port
  }

  depends_on = [
    aws_iam_role_policy_attachment.execution,
    aws_lb_listener.http,
  ]
}
