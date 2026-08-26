#!/usr/bin/env bash
# Demo: reindex non-granule concept types (variables, services, tools, etc.).
# Shows all 11 supported types returning 202, and an unknown type returning 404.
#
# Run: bash demos/06_concept_types.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

KNOWN_TYPES=(
    variables services tools collections
    generics data-quality-summaries order-options
    visualizations subscriptions grid citation
)

header "Trigger reindex for each known concept type"
for t in "${KNOWN_TYPES[@]}"; do
    RESP=$(post "$REINDEXER/reindex/$t")
    STATUS=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('request_id','ERROR: '+d.get('detail','')))" 2>/dev/null)
    echo "  $t  →  $STATUS"
done

header "Unknown concept type returns 404 with type name in detail"
post "$REINDEXER/reindex/does-not-exist" | pp

header "Job detail for the last triggered type"
RESP=$(post "$REINDEXER/reindex/variables")
JOB_ID=$(echo "$RESP" | jfield request_id)
echo "Job ID: $JOB_ID"
sleep 1
curl -s "$REINDEXER/jobs/$JOB_ID" | pp

show_logs_since "$LOG_START"
