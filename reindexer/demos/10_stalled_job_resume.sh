#!/usr/bin/env bash
# Demo: stalled job resumption on startup.
#
# Simulates a job that was interrupted mid-run (e.g. ECS task replacement):
#   1. Write a "running" job with a 15-minute-old heartbeat directly to DynamoDB
#   2. Show /jobs/{id} confirming "running" + stale heartbeat
#   3. Restart the reindexer
#   4. On startup resume_stalled_jobs() detects it, claims it, and re-enqueues
#   5. Show /jobs/{id} confirming heartbeat was refreshed (claimed)
#   6. Show logs confirming the resume event
#
# Run: bash demos/10_stalled_job_resume.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

# Local default; for WL/SIT/PROD set DYNAMODB_ENDPOINT_URL="" to use real AWS.
DYNAMO_URL="${DYNAMODB_ENDPOINT_URL:-http://host.docker.internal:8000}"
STALLED_JOB_ID="stalled-demo-$(date +%s)"
REINDEXER_LOG=/tmp/reindexer.log

# ---------------------------------------------------------------------------
# Helper: inject a stalled job directly into DynamoDB
# ---------------------------------------------------------------------------
inject_stalled_job() {
    python3 - <<PYEOF
import boto3
from datetime import datetime, timedelta, timezone

endpoint = "$DYNAMO_URL"  # bash-expanded; empty string means use real AWS
kwargs = {"region_name": "us-east-1"}
if endpoint:
    kwargs["endpoint_url"] = endpoint
    kwargs["aws_access_key_id"] = "fakekey"
    kwargs["aws_secret_access_key"] = "fakesecret"

ddb = boto3.resource("dynamodb", **kwargs)
table = ddb.Table("cmr-reindexer-jobs")

stale = (datetime.now(timezone.utc) - timedelta(minutes=15)).strftime("%Y-%m-%dT%H:%M:%SZ")
ttl   = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp())

table.put_item(Item={
    "job_id":              "$STALLED_JOB_ID",
    "status":              "running",
    "concept_type":        "granules",
    "started_at":          stale,
    "last_heartbeat":      stale,
    "providers_to_process": ["$PROVIDER_ID"],
    "providers_enqueued":  [],
    "work_items_enqueued": 0,
    "total_dispatched":    0,
    "ttl":                 ttl,
})
print("Stalled job injected:", "$STALLED_JOB_ID")
PYEOF
}

# ---------------------------------------------------------------------------
# Helper: restart the reindexer (same startup flags used in the other demos)
# ---------------------------------------------------------------------------
restart_reindexer() {
    echo "(killing current reindexer...)"
    kill "$(pgrep -f 'uvicorn app.main' | head -1)" 2>/dev/null || true
    sleep 2

    export DB_BACKEND=oracle DB_HOST=host.docker.internal DB_PORT=1521 DB_SERVICE=FREEPDB1
    export DB_USER=METADATA_DB DB_PASSWORD=CHANGE_ME
    export CMR_ELASTIC_HOST=host.docker.internal CMR_GRAN_ELASTIC_HOST=host.docker.internal
    export SQS_ENDPOINT_URL=http://host.docker.internal:4100
    export INTERMEDIATE_QUEUE_URL=http://host.docker.internal:4100/queue/cmr-reindexer-jobs
    export RATE_PER_MINUTE=600000 CMR_ACL_BASE_URL=http://localhost:3011
    export DYNAMODB_ENDPOINT_URL="$DYNAMO_URL"
    export AWS_ACCESS_KEY_ID=fakekey AWS_SECRET_ACCESS_KEY=fakesecret AWS_DEFAULT_REGION=us-east-1

    cd /root/Common-Metadata-Repository/reindexer
    PYTHONPATH=. python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8001 \
        > "$REINDEXER_LOG" 2>&1 &

    echo "(waiting for startup...)"
    local attempts=0
    until curl -s http://localhost:8001/reindexer/health | grep -q '"ok"' || [ $attempts -ge 10 ]; do
        sleep 1; attempts=$((attempts+1))
    done
    echo "Reindexer healthy."
}

# ---------------------------------------------------------------------------
# Step 1 — inject the stalled job
# ---------------------------------------------------------------------------
header "Inject a stalled job (status=running, heartbeat 15 minutes ago)"
inject_stalled_job

header "Job state before restart"
curl -s "$REINDEXER/jobs/$STALLED_JOB_ID" | pp

# ---------------------------------------------------------------------------
# Step 2 — restart so lifespan.resume_stalled_jobs() fires
# ---------------------------------------------------------------------------
header "Restarting reindexer — resume_stalled_jobs() runs on startup"
restart_reindexer

# ---------------------------------------------------------------------------
# Step 3 — observe the outcome
# ---------------------------------------------------------------------------
show_logs_since "$LOG_START"

sleep 1  # allow DynamoDB write to propagate before reading status

header "Job state after restart — heartbeat refreshed (claimed), work re-enqueued"
curl -s "$REINDEXER/jobs/$STALLED_JOB_ID" | pp

echo
echo "What happened:"
echo "  resume_stalled_jobs() scanned DynamoDB for running jobs with heartbeat > 10 min old"
echo "  It claimed the job (conditional write on last_heartbeat prevents double-claim)"
echo "  For concept_type=granules it re-enqueued the remaining providers"
echo "  The job heartbeat is now recent and providers_enqueued will populate as the"
echo "  throttler processes the re-enqueued work items"
