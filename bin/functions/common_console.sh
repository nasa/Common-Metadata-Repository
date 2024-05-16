### Global constants

#Color themes covered are: RGB, RYG, CMYK
BLACK='\033[0;30m'
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[0;37m'

BLACK2='\033[0;40m'
RED2='\033[0;41m'
GREEN2='\033[0;42m'
YELLOW2='\033[0;43m'
BLUE2='\033[0;44m'
MAGENTA2='\033[0;45m'
CYAN2='\033[0;46m'
WHITE2='\033[0;47m'


NC='\033[0m'

BOLD='\033[1m'
FAINT='\033[2m'
UNDERLINE='\033[4m'
BLINK='\033[5m'
REVERSE='\033[7m'

function cprint() {
  msg="$2"
  #if [[ $- == *i* ]]; then
  if tty -s; then
    color="$1"
    printf "${color}%s${NC}" "${msg}"
  else
    printf "%s" "${msg}"
  fi
}

function cprintln() {
  cprint "$1" "$2"
  printf "\n"
}
