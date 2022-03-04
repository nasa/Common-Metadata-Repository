#!/bin/bash

#This directory contains sample files with controlled vocabulary definitions as
#provided by the GCMD Keyword Management System (KMS). The files are used in
#integration tests in order to avoid an external dependency on the GCMD system
#in local testing as well as the CI environment.

#The files with _full designation are the full files from the GCMD system as of
#2018-11-06. In order to speed up the integration tests we use only a few
#samples from each file. If you need to test against the full files you can copy
#the <type>_full to <type>

# You can use the script below to grab all the updated content from the KMS
# service. You would do this if the content or structure of the data has changed
# significently and new data is desired in tests.

schemes_to_import="mimetype dataformat idnnode instruments isotopiccategory \
  locations measurementname platforms projects providers rucontenttype \
  sciencekeywords temporalresolutionrange"

# These types below will return Hits and page info in the headers:
#
# mimetype, dataformat, idnnode, isotopiccategory, measurmentname, rucontenttype,
# temporalresolutionrange
#
# however providers, sciencekeywords (and soon instruments) are past 2000 in
# count and will need to be converted over to paging some time in the future

env='' # sit|uat|prod|<blank> are all posible values
version='' # DRAFT|<blank> are all posible values
additional_flags='' # use this to pass the cache clear param or other flags
stream_mode='off'

# return the url to the server hosting GCMD KMS
function gcmd_host()
{
  raw_url=https://gcmd.${1}.earthdata.nasa.gov
  returned=$(echo "${raw_url}" | \
    sed 's|\.\.|\.|' | \
    sed 's|\.prod\.|\.|')
}

# download one scheme and save it to a file
function fetch_scheme()
{
  gcmd_host "${1}"  #returns a value in "returned"
  host_name=${returned}

  scheme="${2}"
  version="${3}"
  options="${4}"

  echo Exporting ${scheme} from ${host_name}
  # Download the scheme CSV file and save to a file of the same name
  url="${host_name}/kms/concepts/concept_scheme/${scheme}?format=csv&version=${version}${options}"

  if [ "${stream_mode}" == "off" ] ; then
    curl -s ${url} > ${scheme}
  else
    curl -s ${url}
  fi
}

# loop through all the schmems and download them
function work()
{
  for scheme in ${schemes_to_import}
  do
    fetch_scheme "$env" "$scheme" "$version" "$additional_flags"
  done
}

# print out a help and usage message
function help()
{
  echo "Download schemas, send no flags to download all, otherwise you can do this:"
  echo "    update.sh -s rucontenttype -v Draft -w -s platforms -v 1.2.3 -w"
  echo "Meaning:"
  echo "    Download rucontenttype at version 'Draft'",
  echo "    then download platforms at version 1.2.3,"
  echo

  printf '=%.0s' {1..80} ; printf '\n'
  format="%4s %-11s %5s %-42s | %12s\n"
  printf "${format}" Flag Name Input Description Defaults
  printf "${format}" ---- ----------- ----- ------------------------------------------ ------------
  printf "${format}" -a Additional Str "Aditional KMS HTTP parameters to send" "$(echo ${additional_flags} | cut -c -12)"
  printf "${format}" -e Environment Str "Server env: [sit | uat | prod | <blank>]" "${env}"
  printf "${format}" -h Help '' 'Print out a help message' ''
  printf "${format}" -s Scheme List "Schemas, Space delim" "$(echo $schemes_to_import | cut -c -12)"
  printf "${format}" -S Stream Mode "Dump output to stdout and not file" "${stream_mode}"
  printf "${format}" -v Version Str "Scheme version name" "${version}"
  printf "${format}" -w Work '' 'Do the download work' ''
  printf '=%.0s' {1..80} ; printf '\n\n'

  echo "* Additional Default only lists first 12 letters."
  echo "* Schema Default only lists first 12 letters."
}

# assume that work() will be run after parameter processing and not durring
manual_mode="no"

# flags are "tasks" which are run in order, like a mini language
while getopts "a:e:hs:Sv:w" OPTION ; do
  case ${OPTION} in
    a) additional_flags="${OPTARG}" ;;    # set flags and continue
    e) env="${OPTARG}" ;;                 # set env and continue
    h) help ; exit ;;                     # print help and exit
    s) schemes_to_import="${OPTARG}" ;;   # set schemes and continue
    S) stream_mode="on" ;;
    v) version="${OPTARG}" ;;             # set version and continue
    w) manual_mode="yes" ; work ;;        # work() now and not latter, CONTINUE
  esac
done

# if work() has not been requested, then assume it runs here
if [ "${manual_mode}" == "no" ] ; then
  work
fi
