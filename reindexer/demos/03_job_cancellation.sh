#!/usr/bin/env bash
# Demo: cancel a running job.
# Slows the throttle to 10 msg/min so the job stays "running" long enough
# to cancel before the throttler finishes dispatching.
#
# Run: bash demos/03_job_cancellation.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

SLOW_RATE=10   # 10 msg/min → ~6s per granule → job stays alive for ~90s
ORIG_RATE=600000

header "Slow throttle to $SLOW_RATE msg/min"
curl -s -X PUT "$REINDEXER/throttle" \
    -H "Authorization: $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"rate_per_minute\": $SLOW_RATE}" | pp

header "Trigger all-providers granule reindex"
RESP=$(post "$REINDEXER/reindex/granules")
echo "$RESP"
JOB_ID=$(echo "$RESP" | jfield request_id)
echo "Job ID: $JOB_ID"

echo "(waiting 1s for background task to set providers_to_process...)"
sleep 1

header "Job is running — providers enqueued, throttler dispatching slowly"
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

header "Cancel the job"
curl -s -X DELETE "$REINDEXER/jobs/$JOB_ID" \
    -H "Authorization: $TOKEN" | pp

header "Job status is now cancelled"
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

header "Queue depth (work items still in queue but will be skipped by throttler)"
curl -s "$REINDEXER/status" | pp

header "Restore throttle rate"
curl -s -X PUT "$REINDEXER/throttle" \
    -H "Authorization: $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"rate_per_minute\": $ORIG_RATE}" | pp

echo
echo "Note: the cancel cache refreshes every 30s (CANCEL_CHECK_INTERVAL_SECONDS)."
echo "Any work items the throttler picks up before the next refresh will still be"
echo "dispatched. In production (large jobs, slow rates) the cancellation window"
echo "is immediately effective on the next cache refresh."

show_logs_since "$LOG_START"
