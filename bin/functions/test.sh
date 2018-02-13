#!/bin/sh
IFS='' read -r -d '' TEST_HELP <<'EOH'

Usage: cmr test [SUBCOMMANDS]

Defined subcommands:

    all           - Run all tests for all CMR projects.
    help          - Show this message.
    cicd          - Perform the build and testing steps used by CI/CD.
    dep-tree PROJ - Check the given project's depepency tree for JAR file
                    conflicts.
    dep-trees     - Check all projects for JAR file conflicts.
    lint          - Run lint checking task for all projects.
    lint PROJ     - Run lint checking for given project.
    versions      - Check all projects for outdated versions in dependencies.
    versions PROJ - Check the given project for outdated versions in
                    dependencies.

Environment variables required for some of the test tasks:

    CMR_BUILD_UBERJARS
    CMR_DEV_SYSTEM_DB_TYPE
    CMR_DB_URL
    CMR_INTERNAL_NEXUS_REPO

Notes on the ENV variables:

* CMR_BUILD_UBERJARS - If set to 'true', the cicd build task will create the
    application uberjars.
* CMR_DEV_SYSTEM_DB_TYPE - If set to 'external', the command 'cmr setup db'
    will be run by the cicd build task, which also depends upon the following
    ENV variables to be set:
    - CMR_BOOTSTRAP_PASSWORD
    - CMR_INGEST_PASSWORD
    - CMR_METADATA_DB_PASSWORD
* CMR_DB_URL - If CMR_DEV_SYSTEM_DB_TYPE is set to 'external', the caller
    should also set the URL to connect to the external database.
* CMR_ORACLE_JAR_REPO - Oracle libraries are not available in public maven
    repositories. We can host them in internal ones for building. If this is
    set then the maven repo will be updated to use this.
* CMR_INTERNAL_NEXUS_REPO - It set to 'true', the internal CI/CD nexus
    repository will be used in the build and install tasks instead of the
    public Maven Central and Clojars repositories

EOH

function test_all {
    cd $CMR_DIR && lein test
}

function test_cicd {
    cmr build all
    cmr start uberjar dev-system
    if [ $? -ne 0 ] ; then
        echo "Failed to build and start up dev-system" >&2
        echo
        echo "Log for dev-system:"
        cat $CMR_LOG_DIR/dev-system.log
        echo "End of dev-system log."
        echo
        exit 127
    fi
    echo "Timestamp:" `date`
    echo "Running tests ..."
    test_all
    if [ $? -ne 0 ] ; then
        echo "Failed Tests" >&2
        cmr show log-tests
        cmr stop uberjar dev-system
        exit 127
    fi
    cmr show log-tests
    echo "Timestamp:" `date`
    echo "Stopping applications ..."
    cmr stop uberjar dev-system
}

function test_dep_tree_proj {
    PROJ=$1
    cd $CMR_DIR/${PROJ}* && \
        lein do deps :tree, deps :plugin-tree
}

function test_dep_trees {
    cd $CMR_DIR && \
        lein modules do deps :tree, deps :plugin-tree
}

function test_lint_proj {
    PROJ=$1
    cd $CMR_DIR/${PROJ}* && lein lint
}

function test_lint {
    cd $CMR_DIR && lein lint
}

function test_versions_proj {
    PROJ=$1
    cd $CMR_DIR/${PROJ}* && lein check-deps
}

function test_versions {
    cd $CMR_DIR && lein check-deps
}
