#!/bin/bash

# **************************************************************************************************
# This script is meant to be run from the CMR project root by Github.com as a workflow action step.
# The purpouse of this script is to run all the linter checks using clj-kondo in a docker image and
# to return a failed error code if there are any errors.
#
# This script is designed to be called from inside a docker image by github action step like this:
#     run: ./dev-system/support/run-kondo.sh >> report.log

# error or warning
fail_level=error

# set to the string true to always pass, anything else will be considered a false
always_pass=false

# A list of directories to run kondo on
projects_to_check='access-control-app/src \
            acl-lib/src \
            common-app-lib/src \
            common-lib/src \
            elastic-utils-lib/src \
            es-spatial-plugin/src \
            message-queue-lib/src \
            umm-lib/src'

# Call kondo from inside a docker image and then check the exit code for success
work() {
  docker run \
    --volume $PWD:/src \
    --rm cljkondo/clj-kondo \
    sh -c "cd /src && clj-kondo --parallel --fail-level ${fail_level} --lint ${projects_to_check}"
    result=$?

    if [ "${always_pass}" == "true" ]; then
      exit 0
    fi
    exit $result
}

# Explain all the flags the script uses
usage() {
  printf "Run clj-kondo in a docker image\nUsage:\n\n"
  format="%4s %-4s : %s\n"
  printf "$format" Flag Arg Description
  printf "$format" ---- --- -----------
  printf "$format" -p list 'A space deliminated list of projects to check'
  printf "$format" -w '' 'Fail on warnings'
  printf "$format" -a '' 'Always pass, just report'
}

# Process the command line arguments then do the "work" of the script
while getopts "p:wW" opt
do
    case ${opt} in
        p) projects_to_check=$OPTARG ;;
        w) fail_level=warning ;;
        a) always_pass=true ;;
        *) usage ; exit ;;
    esac
done

work
