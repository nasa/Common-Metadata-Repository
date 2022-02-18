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
    pylint *.py \
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
  printf "${format}" '-u' '' 'Unittest' 'Run python unit test'
  printf "${format}" '-l' '' 'Lint' 'Run pylint over all files'
  printf "${format}" '-t' '<token>' 'Token' 'Set token'
  printf "${format}" '-r' '' 'Report' 'Generate code coverage report'
  printf "${format}" '-e' '' 'Example' 'Do curl examples'
  printf "${format}" '-o' '' 'Offline' 'Run serverless offline then exit'
  printf "${format}" '-I' '' 'Install' 'Install dependent libraries'
}

while getopts 'hcCuUlLjt:oreIx' opt; do
  case ${opt} in
    h) help_doc ;;
    c) color_mode='yes';;
    C) color_mode='no' ;;
    d) documentation ;;
    u) python3 -m unittest discover -s ./ -p '*Test.py' ;;
    U) python3 -m unittest discover -s ./ -p '*Test.py' &> test.results.txt ;;
    l) lint ;;
    L) lint &> list.results ;;
    j) pip3 install pytest ; py.test --junitxml junit.xml ;;
    t) token=${OPTARG} ;;
    o) serverless offline ; exit ;;
    r) report_code_coverage ;;
    
    x) rm -rf build script.log ;;

    e)
      baseEndPoint='http://localhost:3000/dev'
      cprintf $YELLOW 'Example calls'
      
      echo
      cprintf $GREEN "Calling /tea"
      curl -s -H 'Cmr-Pretty: true' "${baseEndPoint}/configuration/tea"
      echo
      cprintf $GREEN '----'
      read
      
      echo
      cprintf $GREEN 'Calling /tea/capabilities'
      curl -s "${baseEndPoint}/configuration/tea/capabilities" | head
      echo
      cprintf $GREEN '----'
      read
      
      echo
      cprintf $GREEN 'Calling /tea/status'
      curl -i "${baseEndPoint}/configuration/tea/status"
      echo
      cprintf $GREEN '----'
      
      echo
      cprintf $GREEN 'Calling /tea/provider/POCLOUD, may take 7 seconds'
      curl -H "Cmr-Token: ${token}" "${baseEndPoint}/configuration/tea/provider/POCLOUD"
      echo
      cprintf $GREEN '----'

      echo
      cprintf $GREEN 'Calling /tea/provider/POCLOUD again with just headers, may take 7 seconds'
      curl -I -H "Cmr-Token: ${token}" "${baseEndPoint}/configuration/tea/provider/POCLOUD"
      echo

      #curl -s http://localhost:7000/local-bucket/index.html
      #curl -H 'Cmr-Token: token-value-here' '${baseEndPoint}/configuration/tea/debug'
      ;;
    I)
      #npm install -g serverless
      #curl --silent -o- --location https://slss.io/install | bash
      pip3 install -r requirements.txt 
      serverless plugin install -n serverless-python-requirements
      serverless plugin install -n serverless-s3-local
      ;;
    *) cprintf $RED "option required"
  esac
done

# docker network create localstack
# docker-compose up -d
# curl -i http://localhost:4566/health
# awslocal s3 ls
# serverless plugin install -n serverless-python-requirements
# serverless --config serverless-local.yml deploy --stage local
# serverless deploy --config serverless-local.yml --stage local
