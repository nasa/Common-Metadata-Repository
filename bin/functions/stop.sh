#!/bin/sh
IFS='' read -r -d '' STOP_HELP <<'EOH'

Usage: cmr stop [SUBCOMMANDS]

Defined subcommands:

    help           - Show this message.
    docker APP     - Stop a Docker image for a single CMR application.
    local SERVICE  - Stop a locally run service (allowed: sqs-sns).
    uberdocker OPT - Stop all CMR containers. With the OPT value "separate",
                     individual docker containers will be created; with
                     OPT value "together", all CMR apps will be built in a
                     single container. If none is provided, "together" is
                     assumed.
    uberjar PROJ   - Stop the given project instance.

EOH

function stop_local_sqs_sns () {
    echo "Stopping local SQS/SNS services ..."
    docker stop `cat $SQS_CONTAINER_ID_FILE`
    echo "Done."
}

function stop_uberjar_proj () {
    PROJ=$1
    PID_FILE=`get_pid_file $PROJ`
    PID=`get_pid $PROJ`
    echo "Attempting to stop the CMR using the administrative command URL ..."
    curl -v -XPOST http://localhost:2999/stop
    sleep 3
    if [[ -f "$PID_FILE" && `cmr status uberjar dev-system` = "RUNNING" ]]; then
    	echo "CMR app '$PROJ' still running; forcibly stopping process with pid $PID ..."
        kill -9 $PID
    fi
    if [[ -f "$PID_FILE" && `cmr status uberjar dev-system` != "RUNNING" ]]; then
    	rm $PID_FILE
    fi
}

function stop_docker_proj () {
	APP=$1
    IMAGE_TAG="${DOCKER_CONTAINER_PREFIX}${APP}"
    echo "Stopping $IMAGE_TAG container ..."
    docker stop `cat "${DOCKER_CONTAINER_ID_FILE_PREFIX}${APP}"`
}

function stop_uberdocker () {
    OPT=$1
    if [[ $OPT == "separate" ]]; then
        stop_docker_proj ingest
        stop_docker_proj indexer
        stop_docker_proj index-set
        stop_docker_proj virtual-product
        stop_docker_proj bootstrap
        stop_docker_proj search
        stop_docker_proj access-control
        stop_docker_proj metadata-db
        stop_docker_proj cubby
    elif [[ $OPT == "together" || -z $1 ]]; then
        stop_docker_proj dev-system
    fi
}