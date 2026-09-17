#!/usr/bin/env bash
# Creates the SQS queues in local ElasticMQ before running the service.
# Run once after `cmr start local sqs-sns` is up.
set -euo pipefail

SQS="${SQS_ENDPOINT_URL:-http://localhost:4100}"

for queue in cmr-reindexer-jobs cmr-indexer-jobs; do
    echo "Creating queue: $queue"
    aws --endpoint-url="$SQS" \
        --region us-east-1 \
        sqs create-queue \
        --queue-name "$queue" \
        --output text 2>/dev/null && echo "  created" || echo "  already exists (ok)"
done
