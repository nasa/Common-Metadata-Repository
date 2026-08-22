# CMR semantic search prototype

Internal FastAPI service for importing flattened CMR metadata from S3 and searching a standalone
Elasticsearch vector index. See `GET /version` for its runtime configuration contract.

Run locally with `uvicorn semantic_search.main:app --reload` after setting `ELASTICSEARCH_URL` and
AWS credentials. Imports require `s3:GetObject`; embedding requires `bedrock:InvokeModel`.

## Test-cloud Terraform deployment

The [terraform](terraform/) stack creates an ECR repository, one-task ECS/Fargate service, internal
application load balancer, CloudWatch log group, dedicated ALB/task security groups, and execution
roles. It uses existing VPC subnets and an existing S3 import bucket. It does not create or modify
the CMR Elasticsearch cluster, exporter bucket, subnet routes, NAT gateways, or VPC endpoints.

Copy the variable template and fill in the VPC, subnet, security-group, Elasticsearch, and S3
values:

```bash
cp semantic-search-app/terraform/terraform.tfvars.example \
  semantic-search-app/terraform/terraform.tfvars
terraform -chdir=semantic-search-app/terraform init
terraform -chdir=semantic-search-app/terraform fmt -check
terraform -chdir=semantic-search-app/terraform validate
```

The container must exist before ECS can start. First create only the repository, then build and push
the exact tag configured as `image_tag`:

```bash
terraform -chdir=semantic-search-app/terraform apply \
  -target=aws_ecr_repository.app

ECR_URL="$(terraform -chdir=semantic-search-app/terraform output -raw ecr_repository_url)"
ECR_REGISTRY="${ECR_URL%%/*}"
IMAGE_TAG="test-20260821" # must equal terraform.tfvars

aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker build --platform linux/amd64 -t "$ECR_URL:$IMAGE_TAG" semantic-search-app
docker push "$ECR_URL:$IMAGE_TAG"
```

Use `linux/arm64` instead when `cpu_architecture = "ARM64"`. Complete the deployment after the push:

```bash
terraform -chdir=semantic-search-app/terraform plan -out=semantic-search.tfplan
terraform -chdir=semantic-search-app/terraform apply semantic-search.tfplan
export SEMANTIC_SEARCH_URL="$(terraform -chdir=semantic-search-app/terraform \
  output -raw semantic_search_url)"
```

The ALB is internal. Calls must originate inside the VPC or through existing private connectivity.
Set `client_security_group_ids` to the security groups used by `search-app` and other in-VPC test
clients. A workstation reached through VPN or a bastion may instead require an appropriate entry in
`client_cidr_blocks`. The stack creates a dedicated task security group for ALB traffic and also
attaches `additional_task_security_group_ids`; supplying the working CMR service security group there
preserves security-group-based access to the self-managed Elasticsearch ALB.

Fargate tasks receive no public IP. Their subnets therefore need NAT or VPC endpoints for ECR API,
ECR Docker, CloudWatch Logs, S3, and Bedrock Runtime. They also need a route to the self-managed
Elasticsearch endpoint. The task role can read only the configured S3 prefix and invoke only the
configured Bedrock foundation model. No Elasticsearch credentials are configured.

The load balancer checks `/version`, not `/health`: `/health` correctly returns 503 until the first
successful import creates the semantic alias. After ECS stabilizes, test from an allowed client:

```bash
curl --fail-with-body "$SEMANTIC_SEARCH_URL/version"
curl --fail-with-body -X POST -H 'Content-Type: application/json' \
  "$SEMANTIC_SEARCH_URL/imports" \
  -d '{"s3_uri":"s3://YOUR_BUCKET/semantic-collections/smoke-20260821.jsonl"}'
```

Poll the returned `status_url`. Once it succeeds, `/health` should return 200 and searches can use
`GET /semantic-collections`. Set `search-app`'s semantic service URL to the Terraform
`semantic_search_url` output and enable its semantic-search feature flag only after direct testing.
