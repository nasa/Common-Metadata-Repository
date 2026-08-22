output "lambda_function_name" {
  value = aws_lambda_function.exporter.function_name
}

output "lambda_function_arn" {
  value = aws_lambda_function.exporter.arn
}

output "execution_role_arn" {
  value = aws_iam_role.exporter.arn
}

output "default_s3_uri" {
  value = "s3://${var.s3_bucket_name}/${var.default_s3_key}"
}

