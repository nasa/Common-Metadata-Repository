#!/usr/bin/env bash
# Demo: observability endpoints — /health, /status (ES health + queue depth),
# and /jobs/{id} for historical job lookup.
#
# Run: bash demos/08_status_and_health.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

header "Service health"
curl -s "$REINDEXER/health" | pp

header "Full status (ES cluster health + intermediate queue depth)"
curl -s "$REINDEXER/status" | pp

header "Current throttle rate"
curl -s "$REINDEXER/throttle" | pp

header "Trigger a job and inspect all its fields"
RESP=$(post "$REINDEXER/reindex/granules")
JOB_ID=$(echo "$RESP" | jfield request_id)
sleep 1
echo "Full DynamoDB job record for $JOB_ID:"
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

header "GET /jobs/{id} for non-existent job — 404"
curl -s "$REINDEXER/jobs/00000000-0000-0000-0000-000000000000" | pp

header "Annotated field reference"
cat <<'EOF'
  job_id            UUID assigned at POST time, returned as request_id
  concept_type      granules | granules-by-provider | granules-by-collection | variables | ...
  status            running | completed | failed | cancelled | interrupted
  started_at        ISO8601Z — when create_job was called
  completed_at      ISO8601Z — when mark_job(completed/failed/cancelled) was called
  last_heartbeat    ISO8601Z — updated on every progress write; used for stall detection
  total_dispatched  cumulative concept updates sent to the indexer queue (by throttler)
  work_items_enqueued  collection work items sent to the intermediate queue
  providers_to_process  (granules only) list of provider IDs enumerated at start
  providers_enqueued    (granules only) providers whose collections have been queued
  collection_id     (granules-by-collection only) target collection
  provider_id       (granules-by-provider only) target provider
  ttl               Unix epoch — DynamoDB will auto-delete the item after 30 days
EOF

show_logs_since "$LOG_START"
