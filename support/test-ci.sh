#!/bin/sh

chmod +x support/clean-and-install-dependencies.sh
chmod +x support/build-and-run.sh
chmod +x support/stop.sh
./support/clean-and-install-dependencies.sh
./support/stop.sh
./support/build-and-run.sh

cd ../cmr-system-int-test
CMR_ELASTIC_PORT=9206 lein test-out

cd ../cmr-dev-system
./support/stop.sh