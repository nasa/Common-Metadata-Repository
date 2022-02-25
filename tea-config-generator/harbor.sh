#!/bin/bash

# This script is made to be called from a CI/CD system and manages all the
# docker commands the build process needs to perform.

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

docker_options=''

help_doc()
{
  echo 'Script to manage the life cycle of the Tea Configuration code'
  echo 'Usage:'
  echo '  ./run.sh -[c|C] -t <token> -[hulre]'
  echo '  ./run.sh -[o|I]'
  echo

  format="%4s %-8s %10s | %s\n"
  printf "${format}" 'flag' 'value' 'name' 'Description'
  printf "${format}" '----' '--------' '------' '-------------------------'
  printf "${format}" '-h' '' 'Help' 'Print out a help document'
  printf "${format}" '-c' '' 'Color' 'Turn color on (default)'
  printf "${format}" '-C' '' 'no color' 'Turn color off'
  printf "${format}" '-b' '' 'build' 'Build Docker Image'
  printf "${format}" '-d' '' 'run' 'Deploy serverless tasks'
  printf "${format}" '-r' '' 'run' 'Run Docker Image tasks'
  printf "${format}" '-R' '' 'run' 'Run bash in Docker Image'
}

while getopts 'hcCbrR' opt; do
  case ${opt} in
    h) help_doc ;;
    c) color_mode='yes' ;;
    C) color_mode='no' ; docker_options='--progress plain' ;;
    b) docker build $docker_options --rm --tag=tea-config-gen . ;;
    r)
      # -I install libraries
      # -U unit test, write file
      # -j junit output of tests
      # -l lint report
      docker run --volume $(pwd):/build tea-config-gen \
        sh -c "./run.sh -I -U -j -l"
      ;;
    R) docker run --volume $(pwd):/build -it tea-config-gen bash ;;
    d) #deploy
      docker run --rm \
        --volume $(pwd):/build \
        tea-config-gen \
        sh -c "./run.sh -d"
      ;;
    *) cprintf $RED "option required" ;;
  esac
done
