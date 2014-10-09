#!/bin/sh

# This script will install an elasticsearch plugin into running elasticsearch workload instances.
# IMPORTANT: This does not restart the elastic cluster. That's necessary to do before the plugin will be active.

# It takes three arguments:
# - the path to a plugin zip file
# - The name of the elasticsearch plugin.
# The script copies the zip file to the VM, removes the plugin if it is already installed, installs
# the plugin and restarts elasticsearch.

if [ $# -ne 2 ]
  then
    echo "Requires two arguments: location of plugin file, and the plugin name"
    exit 1
fi

plugin_zip=`pwd`/$1
plugin_name=$2

# The internal ip addresses of the elastic instances
WORKLOAD_HOSTS=("dbrac1node1" "dbrac1node2" "dbrac1node3" "wlelastic1" "wlelastic2" "wlelastic3")
# Intermediate host to copy things to
DEST_HOST="dbrac1node1.dev.echo.nasa.gov"

# The directory of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Copy the plugin into the main ip
echo "Copying $plugin_zip to intermediate host $DEST_HOST."
scp $plugin_zip echo_opr@$DEST_HOST:plugin.zip

for workload_host in ${WORKLOAD_HOSTS[@]}; do
  echo "Installing into ${workload_host}"

  # Copy the plugin to the elastic ip
  cmd="scp plugin.zip ${workload_host}.dev.echo.nasa.gov:."
  ssh echo_opr@$DEST_HOST "$cmd"

  # Uninstall the existing plugin
  nested_cmd="/es/elastic/elastic/deployment/tools/elasticsearch-0.90.7/bin/plugin -remove $plugin_name"
  cmd="ssh $workload_host.dev.echo.nasa.gov '$nested_cmd'"
  ssh echo_opr@$DEST_HOST "$cmd"

  # Install the plugin
  nested_cmd="/es/elastic/elastic/deployment/tools/elasticsearch-0.90.7/bin/plugin -url file:/export/home/echo_opr/plugin.zip -install $plugin_name"
  cmd="ssh $workload_host.dev.echo.nasa.gov '$nested_cmd'"
  ssh echo_opr@$DEST_HOST "$cmd"
done
echo "-------------------------------------------------------------------------------------------"
echo "The plugin is installed on workload nodes. The elastic cluster _must_ _be_ _restarted_ before the new plugin version will be used."