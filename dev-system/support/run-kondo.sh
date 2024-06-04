#!/bin/bash

# **************************************************************************************************
# This script is meant to be run from the CMR project root by Github.com as a workflow action step.
# The purpouse of this script is to run all the linter checks using clj-kondo in a docker image and
# to return a failed error code if there are any errors.

fail_level=error
always_pass=false
projects_to_check='access-control-app/src \
            acl-lib/src \
            common-app-lib/src \
            common-lib/src \
            elastic-utils-lib/src \
            es-spatial-plugin/src \
            message-queue-lib/src'

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

usage() {
  printf "Run clj-kondo in a docker image\nUsage:\n\n"
  format="%4s %-4s : %s\n"
  printf "$format" Flag Arg Description
  printf "$format" ---- --- -----------
  printf "$format" -p list 'A space deliminated list of projects to check'
  printf "$format" -w '' 'Fail on warnings'
  printf "$format" -a '' 'Always pass, just report'
}

# Process the command line arguments
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
