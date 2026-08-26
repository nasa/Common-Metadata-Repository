#!/usr/bin/env bash
# Demo: auth behaviour — missing token, wrong token, valid token.
# GET /throttle and GET /status are open; all POST/PUT/DELETE require auth.
#
# Run: bash demos/07_auth.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

header "GET /health — no auth required"
curl -s "$REINDEXER/health" | pp

header "GET /status — no auth required"
curl -s "$REINDEXER/status" | pp

header "GET /throttle — no auth required"
curl -s "$REINDEXER/throttle" | pp

header "POST /reindex/granules without token — 401"
curl -s -X POST "$REINDEXER/reindex/granules" | pp

header "POST /reindex/granules with bad token — 401 (CMR rejects the token itself)"
curl -s -X POST "$REINDEXER/reindex/granules" \
    -H "Authorization: invalid-token-xyz" | pp

header "POST /reindex/granules with valid token — 202"
post "$REINDEXER/reindex/granules" | pp

header "PUT /throttle without token — 401"
curl -s -X PUT "$REINDEXER/throttle" \
    -H "Content-Type: application/json" \
    -d '{"rate_per_minute": 100}' | pp

header "DELETE /jobs/{id} without token — 401"
curl -s -X DELETE "$REINDEXER/jobs/some-job-id" | pp

header "Echo-Token header also accepted (legacy ECHO clients)"
curl -s -X POST "$REINDEXER/reindex/granules" \
    -H "Echo-Token: $TOKEN" | pp

show_logs_since "$LOG_START"
