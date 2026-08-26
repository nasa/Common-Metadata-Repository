#!/usr/bin/env bash
# Demo: reindex a single concept by concept-id.
# Shows found → 202, not found → 404, and bad format → 400.
#
# Run: bash demos/05_single_concept.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

# Pick the first granule from the test collection
GRANULE_ID=$(curl -s "http://localhost:3003/granules.json?collection-concept-id=$COLLECTION_ID&page_size=1" \
    -H "Authorization: $TOKEN" \
    | python3 -c "import sys,json; entries=json.load(sys.stdin)['feed']['entry']; print(entries[0]['id'] if entries else '')" 2>/dev/null)

if [ -z "$GRANULE_ID" ]; then
    echo "Could not find a granule in collection $COLLECTION_ID — check CMR is running" >&2
    exit 1
fi

echo "Using granule: $GRANULE_ID"

header "Reindex a single known granule"
RESP=$(post "$REINDEXER/reindex/concept/$GRANULE_ID")
echo "$RESP" | pp
JOB_ID=$(echo "$RESP" | jfield request_id)

header "Job status (completes synchronously)"
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

header "Concept not found — 404 (provider exists, granule ID does not)"
post "$REINDEXER/reindex/concept/G9999999999-$PROVIDER_ID" | pp

header "Bad concept-id format — 400 (no DB call made)"
post "$REINDEXER/reindex/concept/not-a-concept-id" | pp

header "All known prefix formats pass validation"
# C and G use per-provider tables; shared types (V, S, TL, ...) need no real provider.
for cid in C1-$PROVIDER_ID G1-$PROVIDER_ID V1-P S1-P TL1-P SUB1-P DQS1-P OO1-P GRD1-P CIT1-P VIS1-P; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$REINDEXER/reindex/concept/$cid" -H "Authorization: $TOKEN")
    echo "  $cid  →  HTTP $STATUS  (404=not found in DB, 202=accepted)"
done

show_logs_since "$LOG_START"
