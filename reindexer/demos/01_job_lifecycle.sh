#!/usr/bin/env bash
# Demo: job lifecycle for a full granule reindex (all providers).
# Shows status transitioning from "running" → "completed" and the
# total_dispatched counter incrementing after the background task finishes.
#
# Run: bash demos/01_job_lifecycle.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

header "Current reindexer status"
curl -s "$REINDEXER/status" | pp

header "Trigger granule reindex for all providers"
RESP=$(post "$REINDEXER/reindex/granules")
echo "$RESP"
JOB_ID=$(echo "$RESP" | jfield request_id)
echo "Job ID: $JOB_ID"

header "Status immediately after 202 — background task not yet done"
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

echo
echo "(waiting 1s for background task to finish...)"
sleep 1

header "Status after background task completes"
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

show_logs_since "$LOG_START"
