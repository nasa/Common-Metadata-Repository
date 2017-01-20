#!/bin/sh
# This script is used to deploy a single application to NGAP.
# Environment vars used by the script
# WORKSPACE_HOME - required. Directory above the cmr root directory.
# DEPLOYMENT_DIR (Optional) - Where the staging dirs for the applications are - defaults to $WORKSPACE_HOME/ngap-deployments.
# This directory must contain a Procfile to launch the application, a .env file to set needed env vars, and a project.clj
# file for the application (simply used by NGAP to identify this as a Clojure deployment).
# NGAP_CLI_DIR (Optional) - Where the NGAP command line utils are installed - defaults to $WORKSPACE_HOME/ngap-cli.
# NGAP commands need to be run from this directory in order to work right due to gem dependencies from what we have seen.

apps=("legacy-services")

environments=("sit" "wl")
app=$1
environment=$2

printUsage() {
  echo "Usage: $0 application-name environment [--skip-build]"
  echo "Creates a tar file for the given app after optionally building the uberjar and deploys it to NGAP."
  echo "--skip-build - Don't build the uberjar, just use the existing one."
}

if [ "$#" -ne 2 -a "$#" -ne 3 ]; then
    printUsage && exit 1
fi

shift
shift

skip_build=false

while [[ $# -gt 0 ]]
do
  key="$1"

  case $key in

      --skip-build)
      skip_build=true
      ;;
      *)
              # unknown option
      ;;
  esac
  shift # past argument or value
done


# Takes two arguments. A value and an array. Checks if the array contains the value.
containsElement () {
  local e
  for e in "${@:2}"; do [[ "$e" == "$1" ]] && return 0; done
  return 1
}

if ! containsElement ${app} "${apps[@]}" ; then
  echo "Unrecognized application name ${app}. Valid applications are [${apps[@]}]." && exit 1
fi

if ! containsElement ${environment} "${environments[@]}" ; then
  echo "Unrecognized environment name ${environment}. Valid environments are [${environments[@]}]." && exit 1
fi

deployment_dir=${DEPLOYMENT_DIR:-"${WORKSPACE_HOME}/ngap-deployments/${environment}/${app}"}
# Different app dir for legacy-services
app_dir=${WORKSPACE_HOME}/cmr/${app}-app && [[ "${app}" -eq "legacy-services" ]] && app_dir=${WORKSPACE_HOME}/cmr-heritage-services/legacy-services
ngap_cli_dir=${NGAP_CLI_DIR:-"${WORKSPACE_HOME}/ngap-cli"}

if [ ! -d "$deployment_dir" ]; then
  echo "The deployment directory ${deployment_dir} does not exist." && exit 1
fi

# Verify that the .env file exists in the deployment directory
if [ ! -f "$deployment_dir/.env" ]; then
  echo "The deployment directory ${deployment_dir} is missing the required .env file. You can override the default directory using \$DEPLOYMENT_DIR env var." && exit 1
fi

# Verify the application directory exists where we expect
if [ ! -d "$app_dir" ]; then
  echo "The application directory ${app_dir} does not exist. Is \$WORKSPACE_HOME set?" && exit 1
fi

# Verify the ngap directory exists where we expect
if [ ! -d "$ngap_cli_dir" ]; then
  echo "The NGAP CLI directory ${ngap_cli_dir} does not exist. Is \$WORKSPACE_HOME set? You can override the default directory using \$NGAP_CLI_DIR env var." && exit 1
fi

# Create the Procfile in the deployment directory
echo "web: java \$JVM_OPTS -cp target/cmr-${app}-app-standalone.jar clojure.main -m cmr.${app}.runner" > "${deployment_dir}/Procfile"

# Create project.clj
echo "(defproject ${app} \"0.1.0-SNAPSHOT\" :description \"${app} App\" :url \"http://example.com/FIXME\" :min-lein-version \"2.0.0\" :dependencies [] :profiles {:dev {:dependencies [[javax.servlet/servlet-api \"2.5\"]  [ring/ring-mock \"0.3.0\"]]} :production {:env {:production true}}})" > "${deployment_dir}/project.clj"

# Clean and create the uberjar
if [ "$skip_build" = false ]; then
  (cd $app_dir && lein do clean, uberjar)
else
  echo "Skipping uberjar build."
fi

# Copy the uberjar file into the deployment target directory
mkdir -p $deployment_dir/target
cp $app_dir/target/cmr-${app}*standalone.jar $deployment_dir/target/cmr-${app}-app-standalone.jar

# Create the tar file from the deployment directory
tars_dir=$deployment_dir/../tarfiles
mkdir -p $tars_dir
tar_file_name=cmr-${app}-${environment}.tar
(cd $deployment_dir && tar -cf ${tars_dir}/${tar_file_name} .)

# Create the ngap deployment
echo "cd $ngap_cli_dir && ngap deployments:create cmr-${app}-${environment} ${tars_dir}/${tar_file_name}"
(cd $ngap_cli_dir && ngap deployments:create cmr-${app}-${environment} ${tars_dir}/${tar_file_name})
