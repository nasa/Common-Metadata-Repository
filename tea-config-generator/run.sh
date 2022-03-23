#!/bin/bash

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

# Check the syntax of the code for PIP8 violations
lint()
{
    printf '*****************************************************************\n'
    printf 'Run pylint to check for common code convention warnings\n'
    pylint *.py tea \
        --disable=duplicate-code \
        --extension-pkg-allow-list=math \
        --ignore-patterns=".*\.md,.*\.sh,.*\.html,pylintrc,LICENSE,build,dist,tags,eo_metadata_tools_cmr.egg-info" \
        > lint.results.txt
    cat lint.results.txt
}

# Run all the Unit Tests
report_code_coverage()
{
    # https://docs.codecov.com/docs/codecov-uploader
    printf '*****************************************************************\n'
    printf 'Run the unit tests for all subdirectories\n'
    pip3 install coverage
    coverage run --source=cmr -m unittest discover
    coverage html
}

# Generate documentation and copy it into a local S3 bucket for viewing
documentation()
{
  if command -v markdown &> /dev/null ; then
    markdown -T public/api.md > public/api.html
  else
    echo "could not found markdown, try running the following"
    cprint $GREEN "brew install discount"
  fi
  if command -v aws &> /dev/null ; then
    aws --endpoint http://localhost:7000 \
      s3 cp public/api.html s3://local-bucket/api.html \
      --profile s3local
  else
    echo "aws command not found"
  fi
}

# Have serverless deploy, called from within the docker container
deploy()
{
  if [ -e ./credentials ] ; then
    # CI/CD creates this file, only install credentials if they exist
    if [ -d ~/.aws ] ; then
      echo 'skipping, do not override'
    else
      mkdir ~/.aws/
      cp ./credentials ~/.aws/.
    fi
  fi
  serverless deploy --stage "${deploy_env}"
}

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
  printf "${format}" '-c' '' 'Color on' 'Turn color on (default)'
  printf "${format}" '-C' '' 'Color off' 'Turn color off'
  printf "${format}" '-d' '' 'Documentation' 'Generate Documentation for AWS'
  printf "${format}" '-D' '' 'Deploy' 'Run deployment tasks'
  printf "${format}" '-u' '' 'Unittest' 'Run python unit test'
  printf "${format}" '-U' '' 'Unittest' 'Run python unit test and save results'
  printf "${format}" '-j' '' 'JUnittest' 'Run python unit test and save junit results'
  printf "${format}" '-l' '' 'Lint' 'Run pylint over all files'
  printf "${format}" '-L' '' 'Lint' 'Run pylint over all files and save results'
  printf "${format}" '-t' '<token>' 'Token' 'Set token'
  printf "${format}" '-r' '' 'Report' 'Generate code coverage report'
  printf "${format}" '-e' '' 'Example' 'Do curl examples'
  printf "${format}" '-o' '' 'Offline' 'Run serverless offline then exit'
  printf "${format}" '-I' '' 'Install' 'Install dependent libraries'
}

while getopts 'hcCdDe:uUlLjt:orSeIx' opt; do
  case ${opt} in
    h) help_doc ;;
    c) color_mode='yes';;
    C) color_mode='no' ;;
    d) documentation ;;
    e) deploy_env=${OPTARG} ;;
    D) deploy ; exit $? ;;
    u) python3 -m unittest discover -s ./ -p '*test.py' ;;
    U) python3 -m unittest discover -s ./ -p '*test.py' &> test.results.txt ;;
    l) lint ;;
    L) lint &> list.results ;;
    j) pip3 install pytest ; py.test --junitxml junit.xml ;;
    t) token=${OPTARG} ;;
    o) serverless offline ; exit ;;
    r) report_code_coverage ;;
    S) serverless doctor &> doctor.txt ;;
    
    x) rm -rf build script.log ;;
    e) curl -H "Authorization: ${token}" "${baseEndPoint}/configuration/tea/provider/POCLOUD" ;;
    I)
      #alternet ways to install serverless, enable as needed
      #npm install -g serverless
      #curl --silent -o- --location https://slss.io/install | bash
      pip3 install -r requirements.txt 
      serverless plugin install -n serverless-offline
      serverless plugin install -n serverless-python-requirements
      serverless plugin install -n serverless-s3-local
      ;;
    *) cprintf $RED "option required" ; exit 42 ;;
  esac
done
