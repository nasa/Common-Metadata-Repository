#!/bin/sh
# This is a script for building and running the dev system for continuous integration.
# It will build and run it in the background.
# CMR_SEPARATE_COLL_INDEX is the environment variable used by indexer to set up granule indexes
# for large collections. It is set to facilitate granule_search_index_name_test in system-int-test.

lein clean
lein uberjar
CMR_SEPARATE_COLL_INDEX='C1-SEP_PROV1,C2-SEP_PROV1' nohup java -jar target/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar&