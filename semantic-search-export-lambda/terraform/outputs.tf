output "ecr_repository_url" {
  value = aws_ecr_repository.exporter.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.exporter.name
}

output "task_definition_arn" {
  value = aws_ecs_task_definition.exporter.arn
}

output "task_role_arn" {
  value = aws_iam_role.task.arn
}

output "subnet_ids" {
  value = var.subnet_ids
}

output "security_group_ids" {
  value = var.security_group_ids
}

output "run_task_network_configuration" {
  value = {
    awsvpcConfiguration = {
      subnets        = var.subnet_ids
      securityGroups = var.security_group_ids
      assignPublicIp = "DISABLED"
    }
  }
}

output "s3_uri_prefix" {
  value = "s3://${var.s3_bucket_name}/${local.s3_prefix}"
}
