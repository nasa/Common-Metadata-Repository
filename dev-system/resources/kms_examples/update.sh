#!/bin/bash

# This directory contains sample files with controlled vocabulary definitions as
# provided by the GCMD Keyword Management System (KMS). The files are used in
# integration tests in order to avoid an external dependency on the GCMD system
# in local testing as well as the CI environment.
#
# The files with _full designation are the full files from the GCMD system.
# In order to speed up the integration tests we use only a few samples from
# each file. If you need to test against the full files you can copy
# the <type>_full to <type>
#
# You can use this script to grab updated content from the KMS service.
# You would do this if the content or structure of the data has changed
# significantly and new data is desired in tests.

schemes_to_import="mimetype dataformat idnnode instruments isotopiccategory \
  locations measurementname platforms projects providers rucontenttype \
  sciencekeywords temporalresolutionrange"

output_dir="."

env='prod'               # sit|uat|prod|<blank> are all possible values
version=''           # DRAFT|<blank> are all possible values
additional_flags=''  # use this to pass the cache clear param or other flags
stream_mode='off'

# Return the URL to the server hosting GCMD KMS
function gcmd_host()
{
  raw_url="https://cmr.${1}.earthdata.nasa.gov"
  returned=$(echo "${raw_url}" | \
    sed 's|\.\.|\.|' | \
    sed 's|\.prod\.|\.|')
}

# Download one scheme and save it to a _full file
function fetch_scheme()
{
  gcmd_host "${1}"  # returns a value in "returned"
  host_name=${returned}

  scheme="${2}"
  version="${3}"
  options="${4}"

  output_file="${output_dir}/${scheme}_full"

  echo "Exporting ${scheme} from ${host_name}"
  url="${host_name}/kms/concepts/concept_scheme/${scheme}?format=csv&version=${version}${options}"

  if [ "${stream_mode}" == "off" ]; then
    curl -s "${url}" > "${output_file}"
    echo "  Saved to ${output_file}"
  else
    curl -s "${url}"
  fi
}

# Loop through all the schemes and download them
function work()
{
  mkdir -p "${output_dir}"

  for scheme in ${schemes_to_import}; do
    fetch_scheme "$env" "$scheme" "$version" "$additional_flags"
  done
}

# Copy all _full files to their shortened names for local testing
function promote()
{
  for scheme in ${schemes_to_import}; do
    full_file="${output_dir}/${scheme}_full"
    short_file="${output_dir}/${scheme}"

    if [ -f "${full_file}" ]; then
      cp "${full_file}" "${short_file}"
      echo "Promoted ${scheme}_full -> ${scheme}"
    else
      echo "Skipping ${scheme}: ${full_file} not found"
    fi
  done
}

# Print out a help and usage message
function help()
{
  echo "Download schemas, send no flags to download all, otherwise you can do this:"
  echo "    $0 -s rucontenttype -v Draft -w -s platforms -v 1.2.3 -w"
  echo "Meaning:"
  echo "    Download rucontenttype at version 'Draft',"
  echo "    then download platforms at version 1.2.3,"
  echo

  printf '=%.0s' {1..80} ; printf '\n'
  format="%4s %-11s %5s %-42s | %12s\n"
  printf "${format}" Flag Name Input Description Defaults
  printf "${format}" ---- ----------- ----- ------------------------------------------ ------------
  printf "${format}" -a Additional Str "Additional KMS HTTP parameters to send" "$(echo ${additional_flags} | cut -c -12)"
  printf "${format}" -e Environment Str "Server env: [sit | uat | prod | <blank>]" "${env}"
  printf "${format}" -h Help '' "Print out a help message" ''
  printf "${format}" -o Output Str "Output directory for _full files" "${output_dir}"
  printf "${format}" -p Promote '' "Copy _full files to shortened names for testing" ''
  printf "${format}" -s Scheme List "Schemes, space delimited" "$(echo $schemes_to_import | cut -c -12)"
  printf "${format}" -S Stream Mode '' "Dump output to stdout, not file" "${stream_mode}"
  printf "${format}" -v Version Str "Scheme version name" "${version}"
  printf "${format}" -w Work '' "Do the download work" ''
  printf '=%.0s' {1..80} ; printf '\n\n'

  echo "* Additional Default only lists first 12 letters."
  echo "* Scheme Default only lists first 12 letters."
}

# Assume that work() will be run after parameter processing and not during
manual_mode="no"

# Flags are "tasks" which are run in order, like a mini language
while getopts "a:e:ho:ps:Sv:w" OPTION; do
  case ${OPTION} in
    a) additional_flags="${OPTARG}" ;;   # set flags and continue
    e) env="${OPTARG}" ;;                # set env and continue
    h) help ; exit ;;                    # print help and exit
    o) output_dir="${OPTARG}" ;;         # set output dir and continue
    p) manual_mode="yes" ; promote ;;   # promote _full to short names, CONTINUE
    s) schemes_to_import="${OPTARG}" ;;  # set schemes and continue
    S) stream_mode="on" ;;               # stream to stdout
    v) version="${OPTARG}" ;;            # set version and continue
    w) manual_mode="yes" ; work ;;       # work() now and not later, CONTINUE
  esac
done

# If work() has not been requested, then assume it runs here
if [ "${manual_mode}" == "no" ]; then
  work
fi
