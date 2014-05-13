#!/bin/sh

# Runs clean and install on all dependencies.
# Must be run from within the cmr-dev-system folder

cd ../cmr-common-lib
lein do clean, install, clean
cd ../cmr-spatial-lib
lein do clean, install, clean
cd ../cmr-umm-lib
lein do clean, install, clean
cd ../cmr-system-trace-lib
lein do clean, install, clean
cd ../cmr-elastic-utils-lib
lein do clean, install, clean
cd ../cmr-transmit-lib
lein do clean, install, clean
cd ../cmr-ingest-app
lein do clean, install, clean
cd ../cmr-search-app
lein do clean, install, clean
cd ../cmr-indexer-app
lein do clean, install, clean
cd ../cmr-index-set-app
cp resources/config/elasticsearch_config.json.template \
resources/config/elasticsearch_config.json
lein do clean, install, clean
cd ../cmr-metadata-db-app
lein do clean, install, clean
cd ../cmr-system-int-test
lein do clean, install, clean

cd ../cmr-dev-system