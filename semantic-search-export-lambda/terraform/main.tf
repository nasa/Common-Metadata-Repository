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
  image_uri  = "${aws_ecr_repository.exporter.repository_url}:${var.image_tag}"
  s3_prefix  = trim(var.s3_prefix, "/")
  s3_objects = local.s3_prefix == "" ? "arn:${data.aws_partition.current.partition}:s3:::${var.s3_bucket_name}/*" : "arn:${data.aws_partition.current.partition}:s3:::${var.s3_bucket_name}/${local.s3_prefix}/*"
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

resource "aws_ecr_repository" "exporter" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "exporter" {
  repository = aws_ecr_repository.exporter.name
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

resource "aws_cloudwatch_log_group" "exporter" {
  name              = "/ecs/${var.task_name}"
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role" "execution" {
  name                 = "${var.task_name}-execution"
  permissions_boundary = var.permissions_boundary_arn
  assume_role_policy   = local.ecs_task_assume_role
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "task" {
  name                 = "${var.task_name}-task"
  permissions_boundary = var.permissions_boundary_arn
  assume_role_policy   = local.ecs_task_assume_role
}

resource "aws_iam_role_policy" "s3_export" {
  name = "s3-export-prefix"
  role = aws_iam_role.task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "WritePromoteAndCleanExport"
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
      Resource = local.s3_objects
    }]
  })
}

resource "aws_ecs_cluster" "exporter" {
  name = var.task_name

  setting {
    name  = "containerInsights"
    value = var.container_insights_enabled ? "enabled" : "disabled"
  }
}

resource "aws_ecs_task_definition" "exporter" {
  family                   = var.task_name
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
    name      = "semantic-search-exporter"
    image     = local.image_uri
    essential = true
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "ELASTICSEARCH_URL", value = var.elasticsearch_url },
      { name = "ELASTICSEARCH_VERIFY_CERTS", value = tostring(var.elasticsearch_verify_certs) },
      { name = "COLLECTION_ALIAS", value = var.collection_alias },
      { name = "VARIABLE_ALIAS", value = var.variable_alias },
      { name = "S3_BUCKET", value = var.s3_bucket_name },
      { name = "S3_PREFIX", value = local.s3_prefix },
      { name = "PAGE_SIZE", value = tostring(var.page_size) },
      { name = "VARIABLE_BATCH_SIZE", value = tostring(var.variable_batch_size) },
      { name = "MAX_COLLECTIONS", value = tostring(var.max_collections) }
    ]
    readonlyRootFilesystem = false
    mountPoints            = []
    volumesFrom            = []
    linuxParameters = {
      initProcessEnabled = true
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.exporter.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "export"
      }
    }
  }])

  depends_on = [aws_iam_role_policy_attachment.execution]
}
