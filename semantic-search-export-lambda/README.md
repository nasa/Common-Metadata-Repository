# CMR semantic-search JSONL export task

This prototype is an on-demand ECS/Fargate task. It reads current collection and variable
Elasticsearch aliases, creates schema-v1 JSONL for `semantic-search-app`, and promotes a fully
written temporary S3 object to a unique versioned key. It does not run an ECS service and has no
load balancer, so it incurs task compute cost only while an export is running.

## Runtime behavior

The task creates keys in this form beneath `S3_PREFIX`:

```text
semantic-collections/collections-20260823T123456Z-<uuid>.jsonl
```

This avoids accidental replacement and preserves every export. `S3_KEY` remains an optional manual
runtime override, but Terraform deliberately does not set it. The output is first uploaded to a
unique staging key, copied to its final key, and then cleaned up. A failure before the copy never
creates the final object.

The object is compact UTF-8 JSONL with a final newline and content type
`application/x-ndjson`. S3 metadata includes schema version, collection and variable counts,
source aliases, and export time. The completion log contains the final bucket, key, counts, bytes,
SHA-256, truncation state, and warnings.

## Configuration

Terraform supplies these task environment variables:

| Variable | Default | Meaning |
|---|---:|---|
| `ELASTICSEARCH_URL` | required | Self-managed Elasticsearch endpoint |
| `COLLECTION_ALIAS` | `collection_search_alias` | Current non-deleted collection alias |
| `VARIABLE_ALIAS` | required in tfvars | Current variable index/alias |
| `S3_BUCKET` | required | Existing destination bucket |
| `S3_PREFIX` | `exports` | Destination prefix and IAM boundary |
| `PAGE_SIZE` | `100` | PIT collection search page size |
| `VARIABLE_BATCH_SIZE` | `500` | IDs per bounded variable query |
| `MAX_COLLECTIONS` | `100000` | Collections requested by the task |
| `ELASTICSEARCH_VERIFY_CERTS` | `true` | TLS certificate verification |

The Python runtime also supports API-key or basic Elasticsearch authentication and S3 encryption
environment variables, but this prototype's Terraform does not put credentials in the task
definition. If authentication is required, add ECS Secrets Manager references rather than plain
environment values. AWS IAM/SigV4 Elasticsearch authentication is not implemented.

Collections and variables are sorted by concept ID. Shared variable associations are attached only
to the first collection by sorted collection ID because the current importer requires variable IDs
to be globally unique. Missing variables and unsupported spatial values are counted as warnings.

## Local tests

Use Python 3.12+ in an isolated environment:

```bash
python3 -m venv /tmp/cmr-export-venv
/tmp/cmr-export-venv/bin/pip install -e './semantic-search-export-lambda[dev]'
/tmp/cmr-export-venv/bin/pytest semantic-search-export-lambda/tests
/tmp/cmr-export-venv/bin/ruff check semantic-search-export-lambda
```

Tests inject fake Elasticsearch and S3 clients and do not use network services.

## Terraform deployment

The [terraform](terraform/) stack creates an immutable ECR repository, ECS cluster, Fargate task
definition, CloudWatch log group, ECS execution role, and a task role scoped to the configured S3
prefix. It does not create an ECS service, S3 bucket, VPC, subnets, security groups, or
Elasticsearch resources.

Copy the example and retain the working subnet and security-group values used by the Lambda. Set
`max_collections` above the expected corpus size so the export completion result reports
`more_matching = false`:

```bash
cp semantic-search-export-lambda/terraform/terraform.tfvars.example \
  semantic-search-export-lambda/terraform/terraform.tfvars
terraform -chdir=semantic-search-export-lambda/terraform init
terraform -chdir=semantic-search-export-lambda/terraform fmt -check
terraform -chdir=semantic-search-export-lambda/terraform validate
```

Create the ECR repository first, then build and push the immutable tag configured in tfvars:

```bash
terraform -chdir=semantic-search-export-lambda/terraform apply \
  -target=aws_ecr_repository.exporter

ECR_URL="$(terraform -chdir=semantic-search-export-lambda/terraform \
  output -raw ecr_repository_url)"
IMAGE_TAG="test-20260823" # must equal terraform.tfvars
ECR_REGISTRY="${ECR_URL%%/*}"

aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin "$ECR_REGISTRY"
./semantic-search-export-lambda/build.sh "$ECR_URL:$IMAGE_TAG" linux/amd64
docker push "$ECR_URL:$IMAGE_TAG"

terraform -chdir=semantic-search-export-lambda/terraform plan -out=exporter.tfplan
terraform -chdir=semantic-search-export-lambda/terraform apply exporter.tfplan
```

Use `linux/arm64` when `cpu_architecture = "ARM64"`.

## Run an export

Terraform retains the network configuration supplied through tfvars and exposes it in AWS CLI
format. Start one task and capture its ARN:

```bash
TF_DIR="semantic-search-export-lambda/terraform"
terraform -chdir="$TF_DIR" output -json run_task_network_configuration \
  > /tmp/semantic-export-network.json

CLUSTER="$(terraform -chdir="$TF_DIR" output -raw ecs_cluster_name)"
TASK_DEFINITION="$(terraform -chdir="$TF_DIR" output -raw task_definition_arn)"

TASK_ARN="$(aws ecs run-task \
  --cluster "$CLUSTER" \
  --task-definition "$TASK_DEFINITION" \
  --launch-type FARGATE \
  --network-configuration file:///tmp/semantic-export-network.json \
  --query 'tasks[0].taskArn' \
  --output text)"
echo "$TASK_ARN"
```

Wait for completion and inspect the exit code and logs:

```bash
aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$TASK_ARN"
aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$TASK_ARN" \
  --query 'tasks[0].{stopCode:stopCode,reason:stoppedReason,containers:containers[*].{exitCode:exitCode,reason:reason}}'
aws logs tail "/ecs/cmr-semantic-search-export-test" --since 1h
```

A successful task exits with code 0. Find the `task_complete` log entry for the exact versioned S3
URI, counts, and checksum. Do not start a second task unless concurrent full exports are intended;
unique final and staging keys make concurrent runs safe, but they duplicate Elasticsearch load.

## Capacity and networking

The exporter retains collection sources, transformed records, and fetched variables in memory. The
task also writes the complete JSONL object under `/tmp` before upload. Start with the supplied 1
vCPU, 2 GiB memory, and 21 GiB Fargate ephemeral storage for roughly 66,000 collections. The disk
allocation is far larger than a typical JSONL export; memory is the resource to watch on the first
full run. Increase `task_memory_mb` to 4096 if Container Insights shows the task approaching its
2 GiB limit. No code or IAM change is needed to increase either tfvars value.

Private task subnets need routes to ECR API/Docker, CloudWatch Logs, S3, and the Elasticsearch
endpoint, through VPC endpoints or NAT as appropriate. The supplied security groups need matching
egress, and Elasticsearch must accept traffic from the task ENI security group. The task role has
only `s3:GetObject`, `s3:PutObject`, and `s3:DeleteObject` beneath `s3_prefix`; it has no Bedrock
permission. Add KMS permissions only if the destination bucket uses a customer-managed key.
