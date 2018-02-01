#!/bin/sh
IFS='' read -r -d '' BUILD_HELP <<'EOH'

Usage: cmr build [SUBCOMMANDS]

With no subcommand, will perform the 'cmr build all' operation.

Defined subcommands:

    all            - Perform all basic build tasks. Prerequisite tasks include
                     installing jars, docs, Gems, and optional perform Oracle
                     build setup. The actual build then creates uberjars for
                     all the projects, including dev-system.
    docker base    - Build the base CMR Docker image.
    docker APP     - Build a Docker image for a single CMR application.
    help           - Show this message.
    uberdocker OPT - Build all CMR containers. With the OPT value "separate",
                     individual docker containers will be created; with
                     OPT value "together", all CMR apps will be built in a
                     single container.
    uberjar PROJ   - Build a specific project lein uberjar.
    uberjars       - Build uberjars for all projects.

Environment variables required for some of the build tasks:

    CMR_BUILD_UBERJARS
    CMR_DB_URL
    CMR_DEV_SYSTEM_DB_TYPE
    CMR_INTERNAL_NEXUS_REPO
    CMR_ORACLE_JAR_REPO

Notes on the ENV variables:

* CMR_BUILD_UBERJARS - If set to 'true', the script will create the application
    uberjars.
* CMR_DEV_SYSTEM_DB_TYPE - If set to 'external', the command 'cmr setup db'
    will be run, which also depends upon the following ENV variables to be set:
    - CMR_BOOTSTRAP_PASSWORD
    - CMR_INGEST_PASSWORD
    - CMR_METADATA_DB_PASSWORD
* CMR_DB_URL - If CMR_DEV_SYSTEM_DB_TYPE is set to 'external', the caller
    should also set the URL to connect to the external database.
* CMR_INTERNAL_NEXUS_REPO - It set to 'true', the internal CI/CD nexus
    repository will be used instead of the public Maven Central and Clojars
    repositories
* CMR_ORACLE_JAR_REPO - Oracle libraries are not available in public maven
    repositories. We can host them in internal ones for building. If this is
    set then the maven repo will be updated to use this.

EOH

function build_uberjar_proj {
    PROJ=$1
    echo "Building '$PROJ' uberjar ..."
    if [ "$CMR_INTERNAL_NEXUS_REPO" = "true" ]; then
        (cd $CMR_DIR/$PROJ && \
            lein with-profile +uberjar,+internal-repos do clean, uberjar)
    else
        (cd $CMR_DIR/$PROJ && \
            lein with-profile +uberjar do clean, uberjar)
    fi
    if [ $? -ne 0 ] ; then
        echo "Failed to generate '$PROJ' uberjar" >&2
        exit 127
    fi
}

function build_uberjars {
    echo "Building uberjars ..."
    if [ "$CMR_INTERNAL_NEXUS_REPO" = "true" ]; then
        (cd $CMR_DIR && \
            lein with-profile +uberjar,+internal-repos modules do clean, uberjar)
    else
        (cd $CMR_DIR && \
            lein with-profile +uberjar modules do clean, uberjar)
    fi
    if [ $? -ne 0 ] ; then
        echo "Failed to generate uberjars" >&2
        exit 127
    fi
}

function build_all {
    cd $CMR_DIR && \
    cmr install jars,docs && \
    cmr install coll-renderer-gems && \
    cmr install orbits-gems && \
    if [ "$CMR_DEV_SYSTEM_DB_TYPE" = "external" ] ; then
        (cd $CMR_DIR && cmr setup db )
        if [ $? -ne 0 ] ; then
            echo "Failed to perform DB setup tasks" >&2
            exit 127
        fi
    fi
    build_uberjars
    build_uberjar_proj dev-system
}

function oracle_download_instructions () {
    JDK_RPM=$1
    echo
    echo "******"
    echo "UPDATE"
    echo "******"
    echo
    echo "Oracle has stopped supporting the automated downloading of Java"
    echo "JDKs. You must now manually agree to the licensing via a web"
    echo "browser before download."
    echo
    echo "CMR uses an archived version of Java 8: jdk-8u161-linux-x64.rpm."
    echo "You may download that binary fle from the archives download page"
    echo "here:"
    echo
    echo "    http://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html"
    echo
    echo "For the purposes of CMR docker builds, the desired JDK rpm needs"
    echo "to be moved to the following location:"
    echo
    echo "    $JDK_RPM"
    echo
    echo "Note that this file will be removed after the build is complete."
    echo
    echo "When that file is in place, you may re-run this command."
    echo
    echo "Exiting ..."
    echo
}

function build_docker_base () {
    JDK_RPM=jdk-8u161-linux-x64.rpm
    IMAGE_TAG=${DOCKER_CONTAINER_PREFIX}base
    BASE_BUILT=`docker images | grep $IMAGE_TAG`
    cd $CMR_DIR/common-app-lib
    if [[ -f "$JDK_RPM"  ]]; then
        docker build -t $IMAGE_TAG .
        rm $JDK_RPM
    else
        oracle_download_instructions $CMR_DIR/common-app-lib/$JDK_RPM
        exit 127
    fi
}

function build_docker_proj () {
    APP=$1
    if [[ "$APP" = "base" ]]; then
        build_docker_base
    else
        IMAGE_TAG="${DOCKER_CONTAINER_PREFIX}${APP}"
        echo "Building $IMAGE_TAG image ..."
        cd $CMR_DIR/${APP}*
        lein uberjar
        docker build -t $IMAGE_TAG .
    fi
}

function build_uberdocker () {
    OPT=$1
    if [[ $OPT == "separate" ]]; then
        cd $CMR_DIR/cubby-app
        build_docker_proj cubby
        cd $CMR_DIR/metadata-db-app
        build_docker_proj metadata-db
        cd $CMR_DIR/access-control-app
        build_docker_proj access-control
        cd $CMR_DIR/search-app
        build_docker_proj search
        cd $CMR_DIR/bootstrap-app
        build_docker_proj bootstrap
        cd $CMR_DIR/virtual-product-app
        build_docker_proj virtual-product
        cd $CMR_DIR/index-set-app
        build_docker_proj index-set
        cd $CMR_DIR/indexer-app
        build_docker_proj indexer
        cd $CMR_DIR/ingest-app
        build_docker_proj ingest
    elif [[ $OPT == "together" || -z $1 ]]; then
        build_docker_proj dev-system
    fi
    cmr clean
}
