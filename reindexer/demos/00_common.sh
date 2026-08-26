#!/usr/bin/env bash
# Sourced by every demo script — common variables and helpers.
# Run all demos from inside the cmr-dev container:
#   cd /root/Common-Metadata-Repository/reindexer
#   bash demos/<script>.sh

set -euo pipefail

REINDEXER="${REINDEXER_URL:-http://localhost:8001}/reindexer"
TOKEN="${CMR_TOKEN:-mock-echo-system-token}"
COLLECTION_ID="${DEMO_COLLECTION_ID:-C1200000002-RXIDXTEST}"
PROVIDER_ID="${DEMO_PROVIDER_ID:-RXIDXTEST}"
REINDEXER_LOG="${REINDEXER_LOG:-/tmp/reindexer.log}"

# Print a section header
header() { echo; echo "=== $* ==="; }

# Pretty-print JSON, fall back to raw output if not valid JSON
pp() {
    local buf
    buf=$(cat)
    echo "$buf" | python3 -m json.tool 2>/dev/null || echo "$buf"
}

# POST and return the response body
post() { curl -s -X POST "$1" -H "Authorization: $TOKEN" "${@:2}"; }

# GET with auth
get_auth() { curl -s "$1" -H "Authorization: $TOKEN"; }

# Extract a field from a JSON response on stdin
jfield() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }

# Show all log lines appended since line $1 (pass LOG_START captured at script top)
show_logs_since() {
    local start_line="${1:-0}"
    header "Log output"
    tail -n +"$((start_line + 1))" "$REINDEXER_LOG" 2>/dev/null | grep -a . || echo "(no log lines)"
}

# Poll GET /jobs/{id} until status matches, or timeout
wait_status() {
    local job_id="$1" target="$2" timeout="${3:-15}" elapsed=0
    while [ $elapsed -lt $timeout ]; do
        status=$(curl -s "$REINDEXER/jobs/$job_id" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','?'))")
        [ "$status" = "$target" ] && return 0
        sleep 1; elapsed=$((elapsed+1))
    done
    echo "Timed out waiting for status=$target (last: $status)" >&2
    return 1
}
