#!/bin/sh
IFS='' read -r -d '' STATUS_HELP <<'EOH'

Usage: cmr status [SUBCOMMANDS]

Defined subcommands:

    help           - Show this message.
    docker         - Show any and all running CMR containers.
    docker APP     - Show the status for a CMR application's Docker image,
                     including dev-system.
    sqs-sns        - Show the status of the local SQS/SNS service.
    uberdocker     - Show the status of the all-in-one, uberdocker container.
    uberjar PROJ   - Show the status of the given project (returns: RUNNING,
                     STOPPED).

EOH

function status_docker_proj () {
    APP=$1
    IMAGE_TAG="${DOCKER_CONTAINER_PREFIX}${APP}"
    docker ps|egrep "CONTAINER|$IMAGE_TAG"
}

function status_docker  () {
    docker ps|egrep 'CONTAINER|cmr*'
}

function status_sqs_sns () {
    docker ps|egrep 'CONTAINER|pafortin/goaws'
}

function status_uberjar_proj () {
    PROJ=$1
    PID_FILE=`get_pid_file $PROJ`
    PID=`get_pid $PROJ`
    case `ps h -o pid $PID|grep -v PID|tail -1` in
        "$PID")
            echo "RUNNING"
            ;;
        *)
            echo "STOPPED"
            ;;
    esac
}

function status_uberdocker () {
    status_docker_proj dev-system
}