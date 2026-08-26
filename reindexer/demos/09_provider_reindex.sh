#!/usr/bin/env bash
# Demo: provider-scoped granule reindex (POST /reindex/granules/provider/{provider_id}).
# Shows job tracking, progress fields, and date filtering at the provider level.
#
# Run: bash demos/09_provider_reindex.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

# PROVIDER_ID sourced from 00_common.sh; override with DEMO_PROVIDER_ID env var.
echo "Using provider: $PROVIDER_ID"

header "Trigger granule reindex for provider $PROVIDER_ID"
RESP=$(post "$REINDEXER/reindex/granules/provider/$PROVIDER_ID")
echo "$RESP" | pp
JOB_ID=$(echo "$RESP" | jfield request_id)

echo "(waiting 1s for background task...)"
sleep 1

header "Job status — note providers_enqueued and work_items_enqueued"
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

header "Provider reindex with date filter (last 7 days)"
AFTER_7D=$(python3 -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)-timedelta(days=7)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
RESP=$(post "$REINDEXER/reindex/granules/provider/$PROVIDER_ID?after=$AFTER_7D")
echo "$RESP" | pp
JOB_ID2=$(echo "$RESP" | jfield request_id)
sleep 1
curl -s "$REINDEXER/jobs/$JOB_ID2" | pp

header "Non-existent provider — job created but background task fails (Oracle table does not exist)"
RESP=$(post "$REINDEXER/reindex/granules/provider/NO_SUCH_PROV")
echo "$RESP" | pp
JOB_ID3=$(echo "$RESP" | jfield request_id)
sleep 1
curl -s "$REINDEXER/jobs/$JOB_ID3" | pp

show_logs_since "$LOG_START"
