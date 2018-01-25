#!/bin/sh
IFS='' read -r -d '' STATUS_HELP <<'EOH'

Usage: cmr status [SUBCOMMANDS]

Defined subcommands:

    help           - Show this message.
    docker         - Show any and all running CMR containers.
    docker APP     - Show the status for a CMR application's Docker image,
                     including dev-system.
    uberjar PROJ   - Show the status of the given project (returns: RUNNING,
                     STOPPED).

EOH

function status_uberjar_proj () {
    PROJ=$1
    PID=`cat ${PID_FILE_PREFIX}${PROJ}`
    case `ps h -o pid $PID` in
        "")
            echo "STOPPED"
            ;;
        "$PID")
            echo "RUNNING"
            ;;
        *)
            echo "UNDEFINED"
            ;;
    esac
}

function status_docker_proj () {
    APP=$1
    IMAGE_TAG="${DOCKER_CONTAINER_PREFIX}${APP}"
    docker ps|egrep "CONTAINER|$IMAGE_TAG"
}

function status_docker  () {
    docker ps|egrep 'CONTAINER|cmr*'
}