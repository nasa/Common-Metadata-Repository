#!/usr/bin/env bash
# Demo: date-range filtering for granule reindex.
# Shows the 30-day default limit, override header, and before/after window.
#
# Run: bash demos/04_date_filtering.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

AFTER_1D=$(python3  -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)-timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
AFTER_31D=$(python3 -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)-timedelta(days=31)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
BEFORE_NOW=$(python3 -c "from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")

header "Reindex granules modified in the last day (within 30-day limit)"
RESP=$(post "$REINDEXER/reindex/granules/collection/$COLLECTION_ID?after=$AFTER_1D")
echo "$RESP" | pp

header "Reindex with after=31 days ago — rejected without override"
post "$REINDEXER/reindex/granules/collection/$COLLECTION_ID?after=$AFTER_31D" | pp

header "Same request with X-CMR-Override-Date-Limit: true — accepted"
post "$REINDEXER/reindex/granules/collection/$COLLECTION_ID?after=$AFTER_31D" \
    -H "X-CMR-Override-Date-Limit: true" | pp

header "Reindex with both after and before (specific window)"
post "$REINDEXER/reindex/granules/collection/$COLLECTION_ID?after=$AFTER_31D&before=$BEFORE_NOW" \
    -H "X-CMR-Override-Date-Limit: true" | pp

header "after >= before — rejected"
post "$REINDEXER/reindex/granules/collection/$COLLECTION_ID?after=2024-06-15T00:00:00Z&before=2024-06-14T00:00:00Z" | pp

header "Bad timestamp format — rejected"
post "$REINDEXER/reindex/granules/collection/$COLLECTION_ID?after=2024-01-01" | pp

header "Offset-aware timestamp (not Z suffix) — rejected"
post "$REINDEXER/reindex/granules/collection/$COLLECTION_ID?after=2024-01-01T00:00:00+00:00" | pp

show_logs_since "$LOG_START"
