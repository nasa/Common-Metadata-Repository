#!/bin/bash

# XXX DEPRECATED!
#
#     The scripts kept in dev-system/support have been migrated to the new CMR
#     command line tool. This script will be removed in the future; please
#     update your CI/CD build plans to set the CMR_INTERNAL_NEXUS_REPO ENV
#     variable and point to the build execution below.

CMR_DIR=`dirname $0`/../..
PATH=$PATH:$CMR_DIR/bin

echo '***'
echo '*** DEPRECATED!'
echo '***'
echo '*** The use of dev-system/support/* scripts is now deprecated. Please'
echo '*** remove all references to them in the Bamboo build scripts and use'
echo '*** the new CMR CLI tool in the top-level bin directory. Note that to'
echo '*** use the internal nexus repository, you will also need to set the'
echo '*** CMR_INTERNAL_NEXUS_REPO environment variable in your scripts.'
echo '***'
cmr build all
cmr start uberjar dev-system