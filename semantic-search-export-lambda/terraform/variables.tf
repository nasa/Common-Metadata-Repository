variable "aws_region" {
  description = "AWS region containing ECS, the VPC, Elasticsearch, ECR, and S3 bucket."
  type        = string
}

variable "task_name" {
  description = "Name used for the on-demand ECS task, cluster, IAM roles, and logs."
  type        = string
  default     = "cmr-semantic-search-export"
}

variable "elasticsearch_url" {
  description = "Base URL for the self-managed Elasticsearch cluster, including scheme and port."
  type        = string

  validation {
    condition     = can(regex("^https?://[^/]+", var.elasticsearch_url))
    error_message = "elasticsearch_url must start with http:// or https:// and include a host."
  }
}

variable "elasticsearch_verify_certs" {
  description = "Whether the Elasticsearch client verifies TLS certificates."
  type        = bool
  default     = true
}

variable "collection_alias" {
  description = "Current/latest collection index alias."
  type        = string
  default     = "collection_search_alias"
}

variable "variable_alias" {
  description = "Current/latest variable index or alias; confirm it for each environment."
  type        = string
}

variable "s3_bucket_name" {
  description = "Name of the existing, manually managed S3 bucket."
  type        = string
}

variable "s3_prefix" {
  description = "Only this bucket prefix is writable by the task role."
  type        = string
  default     = "exports"

  validation {
    condition     = !startswith(var.s3_prefix, "/")
    error_message = "s3_prefix must not start with a slash."
  }
}

variable "subnet_ids" {
  description = "Private subnets used when starting the Fargate task."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) > 0
    error_message = "At least one subnet ID is required."
  }
}

variable "security_group_ids" {
  description = "Security groups attached to the task ENI when starting the task."
  type        = list(string)

  validation {
    condition     = length(var.security_group_ids) > 0
    error_message = "At least one security group ID is required."
  }
}

variable "max_collections" {
  description = "Number of collections exported by each task run."
  type        = number
  default     = 100000

  validation {
    condition     = var.max_collections > 0 && floor(var.max_collections) == var.max_collections
    error_message = "max_collections must be a positive integer."
  }
}

variable "page_size" {
  description = "Elasticsearch collection page size."
  type        = number
  default     = 100

  validation {
    condition     = var.page_size > 0 && floor(var.page_size) == var.page_size
    error_message = "page_size must be a positive integer."
  }
}

variable "variable_batch_size" {
  description = "Maximum variable concept IDs fetched per Elasticsearch request."
  type        = number
  default     = 500

  validation {
    condition     = var.variable_batch_size > 0 && floor(var.variable_batch_size) == var.variable_batch_size
    error_message = "variable_batch_size must be a positive integer."
  }
}

variable "ecr_repository_name" {
  description = "ECR repository created for the exporter container."
  type        = string
  default     = "cmr-semantic-search-export"
}

variable "image_tag" {
  description = "Immutable container image tag used by the task definition."
  type        = string
  default     = "test"
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
  description = "Fargate ephemeral storage for the generated JSONL file."
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
  description = "CloudWatch Logs retention period."
  type        = number
  default     = 14
}

variable "permissions_boundary_arn" {
  description = "Optional IAM permissions boundary required by some accounts."
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags applied through the AWS provider."
  type        = map(string)
  default     = {}
}
