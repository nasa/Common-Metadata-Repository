#!/bin/sh
IFS='' read -r -d '' START_HELP <<'EOH'

Usage: cmr start [SUBCOMMANDS]

Defined subcommands:

    help           - Show this message.
    docker APP     - Run a Docker image for a single CMR application.
    local SERVICE  - Start a locally run service (allowed: sqs-sns).
    uberdocker OPT - Run all CMR containers. With the OPT value "separate",
                     individual docker containers will be created; with
                     OPT value "together", all CMR apps will be built in a
                     single container. If none is provided, "together" is
                     assumed.
    uberjar PROJ   - Start the given project using the standalone JAR file.

EOH

function start_local_sqs_sns () {
    echo "Starting local SQS/SNS services with docker ..."
    rm -f $SQS_CONTAINER_ID_FILE
    docker pull $SQS_DOCKER_REPO
    docker run -d --cidfile=$SQS_CONTAINER_ID_FILE -p 4100:4100 $SQS_DOCKER_REPO
    echo "Done."
}

function start_uberjar_proj () {
    PROJ=$1
    PROJ_UNDERSCORE=`echo $1|sed 's/-/_/g'`
    echo "Starting CMR $PROJ from uberjar ..."
    mkdir -p $CMR_DIR/logs
    JAR=$CMR_DIR/$PROJ/target/cmr-${PROJ}-${CMR_PROJ_VERSION}-standalone.jar
    MAIN=cmr.${PROJ_UNDERSCORE}.runner
    cd $CMR_DIR/$PROJ && \
        clean_es_data && \
        nohup java -classpath $JAR $MAIN > $CMR_DIR/logs/${PROJ}.log 2>&1 &
    echo "$!" > ${PID_FILE_PREFIX}${PROJ}
}

function start_docker_proj () {
    APP=$1
    IMAGE_TAG="${DOCKER_CONTAINER_PREFIX}${APP}"
    CID_FILE="${DOCKER_CONTAINER_ID_FILE_PREFIX}${APP}"
    if [ -f $CID_FILE ]; then
        echo "Container ID file exists for previous run of $APP."
        echo "Attempting to stop old container ..."
        cmr stop docker $APP
        echo "Removing old container ID file ..."
        rm $CID_FILE
    fi
    echo "Starting $IMAGE_TAG container ..."
    PORT=`get_cmr_port $APP`
    docker run -d \
               --cidfile $CID_FILE \
               -p $PORT:$PORT \
               $IMAGE_TAG
}

function switch_and_start_docker_app () {
    APP=$1
    cd "$CMR_DIR/${APP}-app"
    start_docker_proj $APP
}

function start_uberdocker () {
    OPT=$1
    if [[ $OPT == "separate" ]]; then
        switch_and_start_docker_app cubby
        switch_and_start_docker_app metadata-db
        switch_and_start_docker_app access-control
        switch_and_start_docker_app search
        switch_and_start_docker_app bootstrap
        switch_and_start_docker_app virtual-product
        switch_and_start_docker_app index-set
        switch_and_start_docker_app indexer
        switch_and_start_docker_app ingest
    elif [[ $OPT == "together" || -z $1 ]]; then
        echo "Starting $IMAGE_TAG image ..."
        APP=dev-system
        IMAGE_TAG="${DOCKER_CONTAINER_PREFIX}${APP}"
        cd $CMR_DIR/${APP}
        lein uberjar
        docker run \
            -d \
            --cidfile "${DOCKER_CONTAINER_ID_FILE_PREFIX}${APP}" \
            -p 2999:2999 -p 3001:3001 -p 3002:3002 -p 3003:3003 -p 3004:3004 \
            -p 3005:3005 -p 3006:3006 -p 3007:3007 -p 3008:3008 -p 3009:3009 \
            -p 3010:3010 -p 3011:3011 -p 9210:9210 \
            $IMAGE_TAG
    fi
}