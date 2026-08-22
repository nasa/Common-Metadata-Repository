variable "aws_region" {
  description = "AWS region containing the service and its dependencies."
  type        = string
}

variable "service_name" {
  description = "Name used for ECS, ALB, IAM, and logging resources. Keep it at 32 characters or fewer."
  type        = string
  default     = "cmr-semantic-search-test"

  validation {
    condition     = length(var.service_name) <= 32
    error_message = "service_name must be no more than 32 characters."
  }
}

variable "vpc_id" {
  description = "VPC containing the CMR services and self-managed Elasticsearch endpoint."
  type        = string
}

variable "subnet_ids" {
  description = "At least two subnets for the internal ALB and Fargate task."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "At least two subnet IDs are required for the ALB."
  }
}

variable "client_security_group_ids" {
  description = "Security groups, such as search-app's SG, allowed to call the internal ALB."
  type        = list(string)
  default     = []
}

variable "client_cidr_blocks" {
  description = "Optional IPv4 CIDRs allowed to call the internal ALB, for example a VPC or bastion CIDR."
  type        = list(string)
  default     = []
}

variable "additional_task_security_group_ids" {
  description = "Existing SGs also attached to the task ENI; use the CMR service SG if Elasticsearch permits it by SG reference."
  type        = list(string)
  default     = []
}

variable "elasticsearch_url" {
  description = "Self-managed Elasticsearch URL with explicit scheme, host, and port."
  type        = string

  validation {
    condition     = can(regex("^https?://[^/:]+:[0-9]+/?$", var.elasticsearch_url))
    error_message = "elasticsearch_url must include scheme, host, and explicit port, for example http://host:9200."
  }
}

variable "import_s3_bucket_name" {
  description = "Existing bucket containing exporter JSONL objects."
  type        = string
}

variable "import_s3_prefix" {
  description = "Only objects beneath this prefix can be read by semantic-search-app."
  type        = string
  default     = "semantic-collections"

  validation {
    condition     = !startswith(var.import_s3_prefix, "/")
    error_message = "import_s3_prefix must not start with a slash."
  }
}

variable "ecr_repository_name" {
  description = "ECR repository created for the semantic-search container."
  type        = string
  default     = "cmr-semantic-search"
}

variable "image_tag" {
  description = "Container image tag to deploy. Push this tag before the full Terraform apply."
  type        = string
  default     = "test"
}

variable "bedrock_model_id" {
  description = "Bedrock embedding foundation model ID."
  type        = string
  default     = "amazon.titan-embed-text-v2:0"
}

variable "semantic_index_alias" {
  description = "Alias managed atomically after successful imports."
  type        = string
  default     = "semantic_collections"
}

variable "import_control_index" {
  description = "Elasticsearch index containing import job state."
  type        = string
  default     = "semantic_search_imports"
}

variable "embedding_concurrency" {
  type    = number
  default = 4
}

variable "elasticsearch_batch_size" {
  type    = number
  default = 100
}

variable "bedrock_max_attempts" {
  type    = number
  default = 5
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "load_balancer_port" {
  type    = number
  default = 80
}

variable "task_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 1024
}

variable "task_memory_mb" {
  description = "Fargate task memory in MB."
  type        = number
  default     = 2048
}

variable "ephemeral_storage_gib" {
  description = "Fargate ephemeral storage; imports download and validate JSONL here."
  type        = number
  default     = 21

  validation {
    condition     = var.ephemeral_storage_gib >= 21 && var.ephemeral_storage_gib <= 200
    error_message = "ephemeral_storage_gib must be between 21 and 200."
  }
}

variable "cpu_architecture" {
  type    = string
  default = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture must be X86_64 or ARM64."
  }
}

variable "container_insights_enabled" {
  type    = bool
  default = true
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "permissions_boundary_arn" {
  description = "Optional IAM permissions boundary required by the account."
  type        = string
  default     = null
}

variable "tags" {
  type    = map(string)
  default = {}
}

