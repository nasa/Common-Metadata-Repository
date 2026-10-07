#!/usr/bin/env bash
# Creates the indexer queue in local ElasticMQ; run once after `cmr start local sqs-sns`.
set -euo pipefail

SQS="${SQS_ENDPOINT_URL:-http://localhost:4100}"

for queue in cmr-indexer-jobs; do
    echo "Creating queue: $queue"
    aws --endpoint-url="$SQS" \
        --region us-east-1 \
        sqs create-queue \
        --queue-name "$queue" \
        --output text >/dev/null && echo "  created"
done
