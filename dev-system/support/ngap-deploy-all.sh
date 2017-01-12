#!/bin/sh
# This script is used to deploy a single application to NGAP.
# Environment vars used by the script
# WORKSPACE_HOME - required. Directory above the cmr root directory.
# DEPLOYMENT_DIR (Optional) - defaults to $WORKSPACE_HOME/ngap-deployments
# NGAP_CLI_DIR (Optional) - defaults to $WORKSPACE_HOME/ngap-cli

apps=("metadata-db" "cubby" "index-set" "indexer" "virtual-product" "bootstrap" "access-control" "search" "ingest")
# apps=("metadata-db" "index-set" "indexer" "virtual-product" "bootstrap")
environments=("sit" "wl")
environment=$1

printUsage() {
  echo "Usage: $0 environment"
}

if [ "$#" -ne 1 ]; then
    printUsage && exit 1
fi

dir_to_script="$(dirname $0)"

for i in "${apps[@]}"
do
  # $dir_to_script/ngap-deploy.sh $i $environment --skip-build
  $dir_to_script/ngap-deploy.sh $i $environment
done
