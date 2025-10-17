#!/bin/bash

# **************************************************************************************************
# This script is meant to be run from the CMR project root by Github.com as a workflow action step.
# The purpouse of this script is to run all the linter checks using clj-kondo in a docker image and
# to return a failed error code if there are any errors.
#
# This script is designed to be called from inside a docker image by github action step like this:
#     run: ./dev-system/support/run-kondo.sh >> report.log

# error or warning
fail_level=warning

# set to the string true to always pass, anything else will be considered a false
always_pass=false

# Explicitly excluded projects
exclude_list=(
  "indexer-app"
  "ingest-app"
  "metadata-db-app"
  "mock-echo-app"
  "search-app"
)

# Dynamically find all *-lib and *-app directories in the root
projects_to_check=""
for dir in */; do
  dir_name="${dir%/}"  # strip trailing slash
  if [[ "$dir_name" == *-lib || "$dir_name" == *-app ]]; then
    skip=false
    for exclude in "${exclude_list[@]}"; do
      if [[ "$dir_name" == "$exclude" ]]; then
        skip=true
        break
      fi
    done
    if [ "$skip" = false ]; then
      projects_to_check+="$dir_name "
    fi
  fi
done

# Show which projects will be checked
echo "Running clj-kondo on the following projects:"
for project in $projects_to_check; do
  echo "  - $project"
done
echo ""

work() {
  overall_result=0

  # Call kondo for each project individually to stream output
  for project in $projects_to_check; do
    echo "=== Linting project: $project ==="
    docker run \
      --volume "$PWD:/src" \
      --rm cljkondo/clj-kondo \
      sh -c "cd /src && clj-kondo --parallel --fail-level ${fail_level} --lint $project"
    result=$?

    if [ $result -ne 0 ]; then
      overall_result=$result
    fi
    echo ""  # blank line between projects
  done

  if [ "$always_pass" == "true" ]; then
    exit 0
  fi
  exit $overall_result
}

# Explain all the flags the script uses
usage() {
  printf "Run clj-kondo in a docker image\nUsage:\n\n"
  format="%4s %-4s : %s\n"
  printf "$format" Flag Arg Description
  printf "$format" ---- --- -----------
  printf "$format" -p list 'A space delimited list of projects to check'
  printf "$format" -w '' 'Fail on warnings'
  printf "$format" -a '' 'Always pass, just report'
}

# Process the command line arguments then do the "work" of the script
while getopts "p:wa" opt
do
    case ${opt} in
        p) projects_to_check=$OPTARG ;;
        w) fail_level=warning ;;
        a) always_pass=true ;;
        *) usage ; exit ;;
    esac
done

work
