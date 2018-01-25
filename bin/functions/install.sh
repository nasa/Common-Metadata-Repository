#!/bin/sh
IFS='' read -r -d '' INSTALL_HELP <<'EOH'

Usage: cmr install [SUBCOMMANDS]

Defined subcommands:

    coll-renderer-gems - Install Collection Renderer Gem files.
    docs               - Generate documentation and static content.
    help               - Show this message.
    jars               - Install CMR JAR files to local ~/.m2 repository.
    jars,docs          - Install CMR JARs and generate documentation.
    local SERVICE      - Install a local service (allowed: marvel).
    oracle-libs        - Install Oracle JDBC, ONS, and UCP libs. Note that
                         this command expects the respective jar files have
                         already beeb downloaded from the Oracle site amd
                         saved to oracle-lib/support.
    orbits-gems        - Install Orbits Gem files.

Environment variables required for some of the build tasks:

    CMR_INTERNAL_NEXUS_REPO

Notes on the ENV variables:

* CMR_INTERNAL_NEXUS_REPO - It set to 'true', the internal CI/CD nexus
    repository will be used instead of the public Maven Central and Clojars
    repositories

EOH

function install_coll_renderer_gems () {
    # The library is reinstalled after installing gems so that it will contain
    # the gem code.
    echo "Timestamp:" `date`
    echo "Installing collection renderer gems and reinstalling library ..."
    if [ "$CMR_INTERNAL_NEXUS_REPO" = "true" ]; then
        (cd $CMR_DIR/collection-renderer-lib && \
            lein with-profile +internal-repos do install-gems, install, clean)
    else
        (cd $CMR_DIR/collection-renderer-lib \
            && lein do install-gems, install, clean)
    fi
    if [ $? -ne 0 ]; then
        echo "Failed to install gems" >&2
        exit 1
    fi
}

function install_docs () {
    echo "Timestamp:" `date`
    echo "Generating API documentation ..."
    (cd $CMR_DIR && lein generate-static)
    if [ $? -ne 0 ] ; then
        echo "Failed to generate docs" >&2
        exit 1
    fi
}

function install_jars () {
    echo "Timestamp:" `date`
    echo "Installing all apps and libs ..."
    (cd $CMR_DIR && lein 'install-no-clean!')
    if [ $? -ne 0 ] ; then
        echo "Failed to install apps and libs" >&2
        exit 1
    fi
}

function install_jars_docs () {
    echo "Timestamp:" `date`
    echo "Installing all apps, libs, and generating API documentation ..."
    if [ "$CMR_INTERNAL_NEXUS_REPO" = "true" ]; then
        (cd $CMR_DIR && lein 'internal-install-with-content-no-clean!')
    else
        (cd $CMR_DIR && lein 'install-with-content-no-clean!')
    fi
    if [ $? -ne 0 ] ; then
        echo "Failed to install apps, libs, and docs" >&2
        exit 1
    fi
}

function install_local_marvel () {
    # This function needs to be run only after a 'cd' command to the 'dev-system' folder
    cd $CMR_DIR/dev-system &&
    mkdir -p plugins/marvel
    cd plugins/marvel
    curl -O https://download.elasticsearch.org/elasticsearch/marvel/marvel-1.3.0.tar.gz
    tar -zxvf marvel-1.3.0.tar.gz

    # Rename the jar file so that marvel won't attempt to run. This prevents exceptions
    # with "failed to load marvel_index_template.json"
    mv marvel-1.3.0.jar marvel-1.3.0.jar_ignore

    # XXX Let's switch to doing this with Docker instead:
    #     see: https://github.com/docker-library/elasticsearch/issues/32
}

function mvn_oralib_install () {
    JAR=$1
    ARTIFACT_ID=$2

    mvn install:install-file \
        -Dfile=$JAR \
        -DartifactId=$ARTIFACT_ID \
        -Dversion=$ORACLE_VERSION \
        -DgroupId=$ORACLE_GROUP_ID \
        -Dpackaging=jar \
        -DcreateChecksum=true
}

function install_oracle_libs () {
    LIB_DIR=$CMR_DIR/oracle-lib/support
    EXIT=false
    for JAR in $LIB_DIR/ojdbc6.jar $LIB_DIR/ons.jar $LIB_DIR/ucp.jar
    do
        if ! [ -e "$JAR" ] ; then
            echo
            required_file_not_found $JAR
            EXIT=true
        fi
    done
    if [ "$EXIT" = "true" ]; then
        echo
        echo "One or more of the required Oracle library JAR files was not"
        echo "found. Be sure to review the instructions in the following"
        echo "location, and then try again:"
        echo
        echo "    $CMR_DIR/oracle-lib/README.md"
        echo
        exit 127
    fi
    echo "Installing Oracle jars into local maven repository ..."
    mvn_oralib_install $LIB_DIR/ojdbc6.jar ojdbc6 $ORACLE_VERSION
    mvn_oralib_install $LIB_DIR/ons.jar ons $ORACLE_VERSION
    mvn_oralib_install $LIB_DIR/ucp.jar ucp $ORACLE_VERSION
}

function install_orbits_gems () {
    # Orbit gems are only a test time dependency so the library doesn't need
    # to be reinstalled.
    echo "Timestamp:" `date`
    echo "Installing orbits gems ..."
    if [ "$CMR_INTERNAL_NEXUS_REPO" = "true" ]; then
        (cd $CMR_DIR/orbits-lib && \
            lein with-profile +internal-repos install-gems)
    else
        (cd $CMR_DIR/orbits-lib && \
            lein install-gems)
    fi
    if [ $? -ne 0 ] ; then
        echo "Failed to install gems" >&2
        exit 1
    fi
}
