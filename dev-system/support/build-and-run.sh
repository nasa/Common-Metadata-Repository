#!/bin/sh
# This is a script for building and running the dev system for continuous integration.
# It will build and run it in the background.
# CMR_COLLS_WITH_SEPARATE_INDEXES is the environment variable used by indexer to set up granule indexes
# for large collections. It is set to facilitate granule_search_index_name_test in system-int-test.

if [ -d es_data ] ; then
  rm -fr es_data
fi
lein do clean, uberjar
CMR_COLLS_WITH_SEPARATE_INDEXES='C1-SEP_PROV1,C2-SEP_PROV1' nohup java -XX:MaxPermSize=256m -classpath ./target/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar cmr.dev_system.runner&