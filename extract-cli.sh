#!/bin/bash

shopt -s dotglob

SCRIPT_DIR=`dirname $0`

source $SCRIPT_DIR/functions.sh

REPO=cli
PROJ=bin
CONTINUE_FLAG=$1
START_REPO=cmr-preped

git clone $START_REPO $REPO
echo "Cloned start repo to $REPO."
cd $REPO

removal-check $CONTINUE_FLAG

find . -depth 1 \
	! -name "$PROJ" \
	! -name "resources" \
	! -name ".git" \
	-exec rm -rfv {} \;
echo "Removed non-$PROJ files."

MSG="Moved $PROJ into its own repo."
git commit -a -m "$MSG"
echo "$MSG"

prune-repo `pwd`

move-top-level $PROJ

publish $REPO

echo "Done with $PROJ."
