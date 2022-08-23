#!/bin/sh
IFS='' read -r -d '' SETUP_HELP <<'EOH'

Usage: cmr setup [SUBCOMMANDS]

Defined subcommands:

    db               - Perform all DB setup tasks (create-users,
                       do-migrations).
    db create-users  - Run create-user task for apps that have DB users.
    db do-migrations - Run the database migrations against apps that use the
                       database.
    dev              - Install CMR jars, checkouts, and gems locally.
    help             - Show this message.
    profile          - Create a profile for development.
    schemas          - copy the generic schemas from the schemas project to apps that need it locally for development.

Environment variables required for DB setup:

    CMR_BOOTSTRAP_PASSWORD
    CMR_INGEST_PASSWORD
    CMR_METADATA_DB_PASSWORD

EOH

function check_db_env () {
    # Bail out if the required environment variables are not set.
    if [ -z "${CMR_METADATA_DB_PASSWORD}" ] ||
       [ -z "${CMR_BOOTSTRAP_PASSWORD}" ] ||
       [ -z "${CMR_INGEST_PASSWORD}" ]; then
      echo "Failed running the script because one or more of the following environment variables are not set: CMR_METADATA_DB_PASSWORD, CMR_BOOTSTRAP_PASSWORD and CMR_INGEST_PASSWORD" >&2
     exit 127
    fi
}

function setup_db_create_users () {
    check_db_env
    echo "Timestamp:" `date`
    echo "Creating database users ..."
    for i in metadata-db-app bootstrap-app ingest-app; do
        (cd $CMR_DIR/$i && lein create-user)
        if [ $? -ne 0 ] ; then
            echo "Failed to create database users for $i" >&2
            exit 127
        fi
    done
}

function setup_db_do_migrations () {
    check_db_env
    echo "Timestamp:" `date`
    echo "Running database migrations ..."
    cd $CMR_DIR && \
    lein modules :dirs "ingest-app:bootstrap-app:metadata-db-app" migrate
    if [ $? -ne 0 ] ; then
        echo "Failed to run database migrations" >&2
        exit 127
    fi
}

function setup_db () {
    setup_db_create_users && \
    setup_db_do_migrations
}

# Schemas are managed at https://git.earthdata.nasa.gov/scm/emfd/otherschemas.git
# and need to be imported manually into CMR. This function will distribute schemas
# to all the applications that need to consume these schemas and their related files
# to support the Generic Document Pipeline workflow.
# Note: Schemas may be published with any directory case but the schemas are
# consumed in lower case by CMR code.
function setup_schemas () {
  apps=("search-app"
              "ingest-app"
              "indexer-app"
              "metadata-db-app"
              "system-int-test")
  for app in "${apps[@]}"
  do
    dest=$(printf "%s/resources/schemas" $app)
    src=$(printf "%s/schemas/" "." $app)
    printf "Creating %s\n" $dest
    rm -r $CMR_DIR/$dest
    mkdir -p $CMR_DIR/$dest
    for src_name in $(ls $CMR_DIR/schemas | grep -v '.md')
    do
      src_name_lower=$(echo $src_name | awk '{print tolower($0)}')
      printf "\tCopy schemas/%s to %s/%s\n" ${src_name} ${dest} ${src_name_lower}
      cp -R "${CMR_DIR}/schemas/${src_name}/" "${CMR_DIR}/${dest}/${src_name_lower}"
    done
  done
}

function setup_dev () {
    echo "Timestamp:" `date`
    echo "Installing schemas to apps ..."
    setup_schemas
    echo "Timestamp:" `date`
    echo "Installing all apps and generating API documentation ..."
    cd $CMR_DIR && lein 'install-with-content!'
    if [ $? -ne 0 ] ; then
        echo "Failed to install apps and generate docs" >&2
        exit 127
    fi
    echo "Timestamp:" `date`
    rm -r $CMR_DIR/dev-system/checkouts
    echo "Creating dev-system checkouts directory ... "
    (cd $CMR_DIR/dev-system && lein create-checkouts)
    if [ $? -ne 0 ] ; then
        echo "Failed to create checkouts directory" >&2
        exit 127
    fi
    echo "Timestamp:" `date`
    echo "Installing orbit library gems ..."
    (cd $CMR_DIR/orbits-lib && lein install-gems)
    if [ $? -ne 0 ] ; then
        echo "Failed to install gems" >&2
        exit 127
    fi
}

function setup_profile () {
    PROFILE=profiles.clj
    cd $CMR_DIR/dev-system && \
    if [[ ! -f "$PROFILE" ]]; then
        cp profiles.example.clj profiles.clj
    else
        echo "Profile exists; skipping ..."
    fi
}
