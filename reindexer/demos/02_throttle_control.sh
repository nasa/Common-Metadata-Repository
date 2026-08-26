#!/usr/bin/env bash
# Demo: live throttle rate — read and adjust without restarting the service.
#
# Run: bash demos/02_throttle_control.sh

source "$(dirname "$0")/00_common.sh"
LOG_START=$(wc -l < "$REINDEXER_LOG" 2>/dev/null || echo 0)

header "Current throttle rate"
curl -s "$REINDEXER/throttle" | pp

header "Drop rate to 60 msg/min"
curl -s -X PUT "$REINDEXER/throttle" \
    -H "Authorization: $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"rate_per_minute": 60}' | pp

header "Confirm new rate"
curl -s "$REINDEXER/throttle" | pp

header "Restore original rate (600 000 msg/min)"
curl -s -X PUT "$REINDEXER/throttle" \
    -H "Authorization: $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"rate_per_minute": 600000}' | pp

header "Validation: zero rate rejected"
curl -s -X PUT "$REINDEXER/throttle" \
    -H "Authorization: $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"rate_per_minute": 0}' | pp

header "Validation: negative rate rejected"
curl -s -X PUT "$REINDEXER/throttle" \
    -H "Authorization: $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"rate_per_minute": -100}' | pp

show_logs_since "$LOG_START"
