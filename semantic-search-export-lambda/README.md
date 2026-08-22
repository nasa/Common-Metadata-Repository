# CMR semantic-search JSONL export Lambda

This local prototype reads current collection and variable Elasticsearch aliases, creates schema-v1
JSONL for `semantic-search-app`, and promotes a fully written temporary S3 object to the requested
key. It does not query UMM JSON or decode CMR's custom serialized geometry.

## Configuration

Required: `ELASTICSEARCH_URL`. Optional settings are:

| Variable | Default | Meaning |
|---|---:|---|
| `COLLECTION_ALIAS` | `collection_search_alias` | Current non-deleted collection alias |
| `VARIABLE_ALIAS` | `variables` | Current non-deleted variable alias; **confirm in cloud** |
| `S3_BUCKET`, `S3_KEY` | none | Invocation defaults |
| `PAGE_SIZE` | `100` | PIT search page size |
| `VARIABLE_BATCH_SIZE` | `500` | IDs per bounded variable query |
| `MAX_COLLECTIONS` | `1000` | Hard per-invocation ceiling |
| `ELASTICSEARCH_VERIFY_CERTS` | `true` | TLS certificate verification |
| `ELASTICSEARCH_API_KEY` | none | API-key authentication |
| `ELASTICSEARCH_USERNAME`, `ELASTICSEARCH_PASSWORD` | none | Basic authentication |
| `S3_SERVER_SIDE_ENCRYPTION` | bucket policy | For example `aws:kms` or `AES256` |
| `S3_KMS_KEY_ID` | none | Customer-managed KMS key ID |

The event may specify `bucket`, `key`, `provider_ids`, and `max_collections`; event destination and
limit values override defaults. The event limit must be positive and no greater than
`MAX_COLLECTIONS`. Arbitrary Elasticsearch queries are not accepted.

```json
{"bucket":"cmr-semantic-search-demo","key":"exports/smoke-20260819.jsonl",
 "provider_ids":["PROVIDER1"],"max_collections":10}
```

The implementation supports TLS plus API-key or basic authentication. If the test endpoint requires
AWS IAM/SigV4, add an AWS-signing transport compatible with `elasticsearch==8.19.1` after cloud
discovery; do not put credentials in environment values. Lambda and boto3 use the standard AWS
credential chain/execution role.

## Source mapping and output rules

The mapping is based on the repository's collection and variable indexers. Collections use
`concept-id`, `short-name`, `entry-title`, `summary`, `science-keywords`, `platform-sn`,
`instrument-sn`, `temporals`, MBR fields, and `variable-concept-ids`. Variables reliably expose
`concept-id`, `variable-name`, `measurement`, and `definition`. Long name and units are omitted
because they are not reliably indexed.

Temporal coverage is the earliest start and latest end among valid closed, timezone-aware ranges.
Spatial coverage is a closed GeoJSON polygon around a valid non-degenerate, non-antimeridian MBR.
Missing/invalid and antimeridian MBRs are omitted and counted in `warnings`.

Collections and their variables are sorted by concept ID. Associations are deduplicated. The current
semantic importer treats every variable ID in the whole file as globally unique, while CMR can
associate one variable with multiple collections. To honor the consumer without changing its
contract, this exporter attaches a shared variable only to the first collection by sorted collection
ID and increments `duplicate_variable_association`. Revisit this ownership rule if the importer is
changed to scope variable uniqueness per collection.

The object is compact UTF-8 JSONL with a final newline and content type
`application/x-ndjson`. A unique staging key is uploaded, copied to the final key, and deleted. A
failure before copy never creates the final key. Choose a new/versioned final key: S3 copy itself
does not provide a create-only precondition and this prototype must not overwrite existing output.
The return value includes counts, bytes, SHA-256, applied limit, `more_matching`, and warnings.

## Local test and package

Use Python 3.12+ in an isolated environment:

```bash
python3 -m venv /tmp/cmr-export-venv
/tmp/cmr-export-venv/bin/pip install -e './semantic-search-export-lambda[dev]'
/tmp/cmr-export-venv/bin/pytest semantic-search-export-lambda/tests
/tmp/cmr-export-venv/bin/ruff check semantic-search-export-lambda
./semantic-search-export-lambda/build.sh
```

The build produces `semantic-search-export-lambda.zip`; configure the Lambda handler as
`cmr_export.handler.lambda_handler`. Tests inject fake Elasticsearch and S3 clients and never use
network services. `tests/test_export.py` also provides the local fake-client invocation example.

## Terraform deployment

The [terraform](terraform/) directory deploys the built ZIP, Lambda execution role, prefix-scoped
S3 policy, and CloudWatch log group. It deliberately does not create or change the S3 bucket, VPC,
subnets, security groups, Elasticsearch cluster, or their network rules.

Build the ZIP for the Lambda runtime/architecture, copy the example variables, and fill in the
existing bucket and network values:

```bash
./semantic-search-export-lambda/build.sh
cp semantic-search-export-lambda/terraform/terraform.tfvars.example \
  semantic-search-export-lambda/terraform/terraform.tfvars
terraform -chdir=semantic-search-export-lambda/terraform init
terraform -chdir=semantic-search-export-lambda/terraform plan -out=exporter.tfplan
terraform -chdir=semantic-search-export-lambda/terraform apply exporter.tfplan
```

The configured subnets must reach both the Elasticsearch EC2 addresses and S3. For private subnets,
provide an S3 gateway endpoint or NAT path; putting a Lambda in a public subnet does not itself give
the function internet access. The supplied security group needs egress to Elasticsearch's port and
to HTTPS/S3, while the Elasticsearch instance security group must accept traffic from the Lambda
security group. No Elasticsearch authentication environment variables are set by Terraform.

The S3 role policy is limited to `s3_prefix`. Keep `default_s3_key` and event-provided keys beneath
that prefix. Terraform will fail at Lambda creation if the deployment ZIP has not been built at
`deployment_package_path`. If your account requires an IAM permissions boundary, set
`permissions_boundary_arn` in `terraform.tfvars`.

After apply, run a smoke invocation with a unique key:

```bash
aws lambda invoke \
  --function-name cmr-semantic-search-export-test \
  --cli-binary-format raw-in-base64-out \
  --payload '{"key":"exports/smoke-20260820.jsonl","max_collections":10}' \
  /tmp/cmr-export-response.json
python3 -m json.tool /tmp/cmr-export-response.json
```

## Deployment requirements

Use a Python 3.12 Lambda matching the architecture used to build dependencies. Start with 1 GB
memory, a 15-minute timeout, and at least 1 GB ephemeral storage; adjust only after measuring a
smoke run. The complete output is staged in `/tmp`, so required ephemeral storage is the packaged
runtime plus the expected JSONL size and overhead. Set reserved concurrency to 1, and use unique
versioned keys to prevent concurrent/accidental replacement.

Place the function in subnets/security groups that can reach the internal Elasticsearch endpoint.
Its role needs read access to only the two aliases and `s3:PutObject`, `s3:GetObject` (for server-side
copy), and `s3:DeleteObject` on the export prefix. Add `kms:Encrypt`/`kms:Decrypt` only when the bucket
uses a customer KMS key, and `secretsmanager:GetSecretValue` only if credentials are stored there.
It needs no Bedrock permission. Logs contain destinations, aliases, counts, timings/warnings, and
sanitized exception categories—not credentials, authorization headers, documents, or output rows.

## Human operator runbook

Do these read-only checks from an authenticated host that can reach the test cluster (replace the
URL and authorization mechanism; never paste credentials into tickets or logs):

```bash
curl --fail --silent --show-error "$ES_URL/_alias/collection_search_alias?pretty"
curl --fail --silent --show-error "$ES_URL/_alias/*variable*?pretty"
curl --fail --silent --show-error "$ES_URL/collection_search_alias/_mapping?pretty"
curl --fail --silent --show-error "$ES_URL/CONFIRMED_VARIABLE_ALIAS/_mapping?pretty"
curl --fail --silent --show-error -H 'Content-Type: application/json' \
  "$ES_URL/collection_search_alias/_search?size=1" \
  -d '{"query":{"bool":{"filter":[{"term":{"deleted":false}}]}},"_source":["concept-id","short-name","entry-title","summary","provider-id","science-keywords","science-keywords-flat","platform-sn","instrument-sn","temporals","mbr-*","variable-concept-ids"]}'
curl --fail --silent --show-error -H 'Content-Type: application/json' \
  "$ES_URL/CONFIRMED_VARIABLE_ALIAS/_search?size=1" \
  -d '{"query":{"bool":{"filter":[{"term":{"deleted":false}}]}},"_source":["concept-id","variable-name","measurement","definition"]}'
```

Confirm alias targets are current/latest-only, exact field names/types, authentication mode, and
whether PIT plus `_shard_doc` is enabled. Then deploy the zip with a **new versioned output key** and
invoke a 10-collection event. Verify CloudWatch's completion record and download the object:

```bash
aws s3 cp s3://cmr-semantic-search-demo/exports/smoke-20260819.jsonl /tmp/smoke.jsonl
wc -l -c /tmp/smoke.jsonl
python3 -m json.tool --json-lines /tmp/smoke.jsonl >/dev/null
sha256sum /tmp/smoke.jsonl
```

Compare line count, bytes, and SHA-256 to the Lambda result; inspect representative records without
publishing sensitive metadata. If correct, invoke another new key with `max_collections` no greater
than 1000. Import the resulting object into the internal semantic service:

```bash
curl --fail --silent --show-error -X POST -H 'Content-Type: application/json' \
  "$SEMANTIC_SEARCH_URL/imports" \
  -d '{"s3_uri":"s3://cmr-semantic-search-demo/exports/collections-20260819.jsonl"}'
```

Poll the returned status URL until it succeeds, then compare its collection count to the export.

## Not verified locally

Live alias names (especially the variable alias), deployed mapping/version drift, endpoint
authentication, CA trust, PIT support, VPC routing/security groups, execution-role index access,
Lambda runtime architecture, run duration/memory/ephemeral usage, S3 copy permissions, bucket
encryption policy, KMS policy, and the semantic service's access to the resulting S3 object all
require test-cloud verification.
