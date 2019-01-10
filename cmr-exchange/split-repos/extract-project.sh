#!/bin/bash

SCRIPT_DIR=`dirname $0`

source $SCRIPT_DIR/functions.sh

shopt -s dotglob

REPO=$1
PROJ=$1
GIT_REPO=`project-to-repo-name $REPO`
CONTINUE_FLAG=$2
START_REPO=cmr-preped

arg-check $PROJ

clone-repo $START_REPO $REPO
cd $REPO

removal-check $CONTINUE_FLAG

remove-non-proj-files $PROJ

MSG="Moved $PROJ into its own repo."
git commit -a -m "$MSG"
echo "$MSG"

prune-repo `pwd`

move-top-level $PROJ

publish $GIT_REPO

echo "Done with $PROJ."
