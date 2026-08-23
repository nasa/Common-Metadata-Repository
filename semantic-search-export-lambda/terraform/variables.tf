variable "aws_region" {
  description = "AWS region containing the Lambda, VPC, and S3 bucket."
  type        = string
}

variable "function_name" {
  description = "Lambda function name."
  type        = string
  default     = "cmr-semantic-search-export"
}

variable "deployment_package_path" {
  description = "Absolute path, or path relative to this Terraform directory, to the built Lambda ZIP."
  type        = string
  default     = "../semantic-search-export-lambda.zip"
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
  description = "Whether the Elasticsearch client verifies TLS certificates. Has no effect for HTTP URLs."
  type        = bool
  default     = true
}

variable "collection_alias" {
  description = "Current/latest collection index alias."
  type        = string
  default     = "collection_search_alias"
}

variable "variable_alias" {
  description = "Current/latest variable index or alias. This is environment-specific and must be confirmed from Elasticsearch."
  type        = string
}

variable "s3_bucket_name" {
  description = "Name of the existing, manually managed S3 bucket."
  type        = string
}

variable "s3_prefix" {
  description = "Only this existing bucket prefix is writable by the Lambda role; do not include a leading slash."
  type        = string
  default     = "exports"

  validation {
    condition     = !startswith(var.s3_prefix, "/")
    error_message = "s3_prefix must not start with a slash."
  }
}

variable "default_s3_key" {
  description = "Default output key. Invocation events can override it but IAM still restricts writes to s3_prefix."
  type        = string
  default     = "exports/collections.jsonl"
}

variable "subnet_ids" {
  description = "Private subnet IDs with network routes to Elasticsearch and S3 access via NAT or an S3 VPC endpoint."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) > 0
    error_message = "At least one subnet ID is required."
  }
}

variable "security_group_ids" {
  description = "Security group IDs allowing egress to the Elasticsearch EC2 cluster and HTTPS access to S3."
  type        = list(string)

  validation {
    condition     = length(var.security_group_ids) > 0
    error_message = "At least one security group ID is required."
  }
}

variable "max_collections" {
  description = "Hard upper limit for an invocation's max_collections value."
  type        = number
  default     = 1000

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

variable "runtime" {
  description = "Lambda Python runtime used for the deployment package."
  type        = string
  default     = "python3.12"
}

variable "architecture" {
  description = "Lambda instruction-set architecture. Build dependencies for the same architecture."
  type        = string
  default     = "x86_64"

  validation {
    condition     = contains(["x86_64", "arm64"], var.architecture)
    error_message = "architecture must be x86_64 or arm64."
  }
}

variable "memory_size_mb" {
  description = "Lambda memory allocation in MB."
  type        = number
  default     = 1024
}

variable "timeout_seconds" {
  description = "Lambda timeout in seconds (maximum 900)."
  type        = number
  default     = 900

  validation {
    condition     = var.timeout_seconds >= 1 && var.timeout_seconds <= 900
    error_message = "timeout_seconds must be between 1 and 900."
  }
}

variable "ephemeral_storage_mb" {
  description = "Lambda /tmp storage in MB."
  type        = number
  default     = 1024

  validation {
    condition     = var.ephemeral_storage_mb >= 512 && var.ephemeral_storage_mb <= 10240
    error_message = "ephemeral_storage_mb must be between 512 and 10240."
  }
}

variable "reserved_concurrency" {
  description = "Reserved executions. Keep at 1 to avoid concurrent writes to the same default key."
  type        = number
  default     = 1
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention period."
  type        = number
  default     = 14
}

variable "permissions_boundary_arn" {
  description = "Optional IAM permissions boundary ARN required by some accounts."
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags applied through the AWS provider."
  type        = map(string)
  default     = {}
}
