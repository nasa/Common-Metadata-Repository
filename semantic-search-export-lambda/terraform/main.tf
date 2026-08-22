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

locals {
  package_path = startswith(var.deployment_package_path, "/") ? var.deployment_package_path : "${path.module}/${var.deployment_package_path}"
  s3_prefix    = trim(var.s3_prefix, "/")
  s3_objects   = local.s3_prefix == "" ? "arn:${data.aws_partition.current.partition}:s3:::${var.s3_bucket_name}/*" : "arn:${data.aws_partition.current.partition}:s3:::${var.s3_bucket_name}/${local.s3_prefix}/*"
}

data "aws_partition" "current" {}

resource "aws_cloudwatch_log_group" "exporter" {
  name              = "/aws/lambda/${var.function_name}"
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role" "exporter" {
  name                 = "${var.function_name}-execution"
  permissions_boundary = var.permissions_boundary_arn

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "vpc_execution" {
  role       = aws_iam_role.exporter.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy" "s3_export" {
  name = "s3-export-prefix"
  role = aws_iam_role.exporter.id

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

resource "aws_lambda_function" "exporter" {
  function_name = var.function_name
  description   = "Exports current CMR collection and variable metadata to semantic-search JSONL"
  role          = aws_iam_role.exporter.arn
  runtime       = var.runtime
  architectures = [var.architecture]
  handler       = "cmr_export.handler.lambda_handler"

  filename         = local.package_path
  source_code_hash = filebase64sha256(local.package_path)

  memory_size                    = var.memory_size_mb
  timeout                        = var.timeout_seconds
  reserved_concurrent_executions = var.reserved_concurrency

  ephemeral_storage {
    size = var.ephemeral_storage_mb
  }

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = var.security_group_ids
  }

  environment {
    variables = {
      ELASTICSEARCH_URL          = var.elasticsearch_url
      ELASTICSEARCH_VERIFY_CERTS = tostring(var.elasticsearch_verify_certs)
      COLLECTION_ALIAS           = var.collection_alias
      VARIABLE_ALIAS             = var.variable_alias
      S3_BUCKET                  = var.s3_bucket_name
      S3_KEY                     = var.default_s3_key
      PAGE_SIZE                  = tostring(var.page_size)
      VARIABLE_BATCH_SIZE        = tostring(var.variable_batch_size)
      MAX_COLLECTIONS            = tostring(var.max_collections)
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.exporter,
    aws_iam_role_policy_attachment.vpc_execution,
    aws_iam_role_policy.s3_export,
  ]
}
