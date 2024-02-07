#!/bin/sh
IFS='' read -r -d '' INSTALL_HELP <<'EOH'

Usage: cmr install [SUBCOMMANDS]

Defined subcommands:

    docs               - Generate documentation and static content.
    help               - Show this message.
    jars               - Install CMR JAR files to local ~/.m2 repository.
    jars,docs          - Install CMR JARs and generate documentation.
    local SERVICE      - Install a local service (allowed: spatial_plugin).
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

function install_local_spatial_plugin () {
  # Install deps
  mkdir -p $CMR_DIR/dev-system/resources/elasticsearch/es_libs/

  printf "\naction install-es-deps\n"
  (cd $CMR_DIR/es-spatial-plugin && \
  lein install-es-deps && \
  cp es-deps/cmr-es-spatial-plugin-deps-0.1.0-SNAPSHOT-standalone.jar $CMR_DIR/dev-system/resources/elasticsearch/es_libs/)

  # Install plugin
  rm -rf $CMR_DIR/dev-system/resources/elasticsearch/plugins/cmr_spatial
  mkdir -p $CMR_DIR/dev-system/resources/elasticsearch/plugins/cmr_spatial

  printf "\naction package-es-plugin\n"
  (cd $CMR_DIR/es-spatial-plugin && \
  lein package-es-plugin && \
  cp target/cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip $CMR_DIR/dev-system/resources/elasticsearch/plugins/cmr_spatial/ && \
  cd $CMR_DIR/dev-system/resources/elasticsearch/plugins/cmr_spatial/ &&
  unzip cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip)
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
