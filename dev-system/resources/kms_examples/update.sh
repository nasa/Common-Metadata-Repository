#!/bin/bash

#This directory contains sample files with controlled vocabulary definitions as
#provided by the GCMD Keyword Management System (KMS). The files are used in
#integration tests in order to avoid an external dependency on the GCMD system
#in local testing as well as the CI environment.

#The files with _full designation are the full files from the GCMD system as of
#2018-11-06. In order to speed up the integration tests we use only a few
#samples from each file. If you need to test against the full files you can copy
#the <type>_full to <type>

# you can use the script below to grab all the updated content

schemes_to_import="granuledataformat idnnode instruments isotopiccategory \
  locations measurementname platforms projects providers rucontenttype \
  sciencekeywords temporalresolutionrange"

# These types below will return Hits and page info in the headers:
#
# granuledatafile, idnnode, isotopiccategory, measurmentname, rucontenttype,
# temporalresolutionrange
#
# however providers, sciencekeywords (and soon instruments) are past 2000 in
# count and will need to be converted over to paging some time in the future

env='' # sit|uat|prod|<blank> are all posible values
version='' # DRAFT|<blank> are all posible values

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
  gcmd_host "${1}"
  host_name=${returned}
  echo Exporting ${2} from ${host_name}
  curl -s \
    "${host_name}/kms/concepts/concept_scheme/${2}?format=csv&version=${3}" \
    > ${2}
}

# loop through all the schmems and download them
function work()
{
  for scheme in ${schemes_to_import}
  do
    fetch_scheme "$env" "$scheme" "$version"
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

  format="%4s %-12s %s\n"
  printf "${format}" Flag Name Description
  printf "${format}" ---- ------------ -----------
  printf "${format}" -e Environment "Server environment, sit,uat,prod,<blank>"
  printf "${format}" -h Help "Print out a help message"
  printf "${format}" -s Scheme "Space deliminated list of schems to download"
  printf "${format}" -v Version "Scheme version name"
  printf "${format}" -w Work "Do the download work"
}

manual_mode="no"

while getopts "e:hs:v:w" OPTION ; do
  case ${OPTION} in
    e) env="${OPTARG}" ;;
    h) help ; exit ;;
    s) schemes_to_import="${OPTARG}" ;;
    v) version="${OPTARG}" ;;
    w) manual_mode="yes" ; work ;;
  esac
done

if [ "${manual_mode}" == "no" ] ; then
  work
fi
