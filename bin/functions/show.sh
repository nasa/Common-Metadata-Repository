#!/bin/sh
IFS='' read -r -d '' SHOW_HELP <<'EOH'

Usage: cmr show [SUBCOMMANDS]

Defined subcommands:

    cmr-port APP  - Show the default port for the given CMR port.
    help          - Show this message.
    log PROJ      - Display the contents of the given project's log file.
    log-tail PROJ - Tail the log file of the given project.
    log-test PROJ - Display the contents of the given project's logged test output.
    log-tests     - Display all output for all project tests.

EOH

function show_log_proj {
    PROJ=$1
    cat $CMR_DIR/logs/${PROJ}.log
}

function show_log_tail_proj {
    PROJ=$1
    tail -f $CMR_DIR/logs/${PROJ}.log
}

function show_log_test_proj {
    PROJ=$1
    cat $CMR_DIR/${PROJ}*/testreports.xml $CMR_DIR/${PROJ}/test2junit/xml/*.xml
}

function show_log_tests {
    cat $CMR_DIR/*/testreports.xml $CMR_DIR/*/test2junit/xml/*.xml
}
