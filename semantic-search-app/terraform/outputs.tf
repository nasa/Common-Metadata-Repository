output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "container_image" {
  value = local.image_uri
}

output "semantic_search_url" {
  value = "http://${aws_lb.app.dns_name}:${var.load_balancer_port}"
}

output "load_balancer_dns_name" {
  value = aws_lb.app.dns_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.app.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "task_role_arn" {
  value = aws_iam_role.task.arn
}

