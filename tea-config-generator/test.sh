#!/bin/bash

# Test the TEA Config Generator in AWS. A CMR token and the AWS API Gateway 
# Server instance must be provided

# ##############################################################################
# Values

aws_instance=""
base_url="https://${aws_instance}.execute-api.us-east-1.amazonaws.com/dev"

# ##############################################################################
# Color Util

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
WHITE='\033[0;37m'
BOLD='\033[1m'
UNDERLINE='\033[4m'
NC='\033[0m' # No Color
color_mode='yes'

cprintf() {
    color=$1
    content=$2
    if [ "$color_mode" == "no" ]
    then
        printf "${content}\n"
    else
        printf "${color}%s${NC}\n" "${content}"
    fi
}

# ##############################################################################
# Functions

function capabilities
{
  url="$base_url/configuration/tea/capabilities"
  cprintf $GREEN "calling $url"
  curl -s -H 'Cmr-Pretty: true' "$url"
}

function status
{
  url="$base_url/configuration/tea/status"
  cprintf $GREEN "calling $url"
  curl -is -H 'Cmr-Pretty: true' "$url"
}

function debug
{
  url="$base_url/configuration/tea/debug"
  cprintf $GREEN "calling $url"
  curl -is -H 'Cmr-Pretty: true' \
    -H "authorization: Bearer ${token}" \
    "$url"
}

function generate
{
  url="$base_url/configuration/tea/provider/POCLOUD"
  cprintf $GREEN "calling $url"
  curl -si \
    -H "Authorization: Bearer ${token}" \
    -H 'Cmr-Pretty: true' \
    "$url"
 }

help_doc()
{
  echo 'Script to call interfaces on AWS'
  echo 'Usage:'
  echo '  ./test.sh -a <aws> -t <file> [-c | -d | -g | -s ]'
  echo
  echo '  ./test.sh -a yruab01 -t /Users/MacUser/.hidden/token-in-file.txt -g'
  echo
  
  format="%4s %-6s %12s | %s\n"
  printf "${format}" 'Flag' 'Value' 'Name' 'Description'
  printf "${format}" '----' '------' '------------' '-------------------------'
  printf "${format}" '-h' '' 'Help' 'Print out a help document.'
  printf "${format}" '-a' '<aws>' 'AWS' 'REQUIRED: API Gateway host name added in front of execute-api.us-east-1.amazonaws.com.'
  printf "${format}" '-c' '' 'Capabilities' 'Show capabilities'
  printf "${format}" '-d' '' 'Debug' 'display debug info'
  printf "${format}" '-g' '' 'Generate' 'Generate the tea config YAML file.'
  printf "${format}" '-s' '' 'Status' 'check service status.'
  printf "${format}" '-t' '<file>' 'Token' 'REQUIRED: File with a CMR token, first line without a pound is used.'
}

while getopts 'ha:cdgst:' opt; do
  case ${opt} in
    h) help_doc ;;
    a) aws_instance="${OPTARG}"
      base_url="https://${aws_instance}.execute-api.us-east-1.amazonaws.com/dev"
      ;;
    c) capabilities ;;
    d) debug ;;
    g) generate ;;
    s) status ;;
    t) token=$(grep -v '#' ${OPTARG} | head -n 1) ;;
    *) cprintf $RED "option required" ; help_doc ;;
  esac
done
