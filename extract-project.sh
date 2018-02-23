#!/bin/bash

shopt -s dotglob

REPO=$1
PROJ=$1
GIT_REPO=`echo $REPO|sed 's/-app$//'`
CONTINUE_FLAG=$2
START_REPO=cmr-preped

if [[ -z "$PROJ" ]]; then
	echo "ERROR: You must provide a project directory."
	exit 127
fi

git clone $START_REPO $REPO
echo "Cloned start repo to $REPO."
cd $REPO

if [[ "$CONTINUE_FLAG" != "-y" ]]; then
	echo -n "Preparing to remove files in `pwd`; continue? [y/N]"
	read RESPONSE
	if [[ "$RESPONSE" != "y" ]]; then
		echo "Cancelled."
		exit 127
	fi
fi

find . -depth 1 ! -name "$PROJ" ! -name ".git" -exec rm -rfv {} \;
echo "Removed non-$PROJ files."

MSG="Moved $PROJ into its own repo."
git commit -a -m "$MSG"
echo "$MSG"

git ls-files > keep-these.txt
echo "Created list of files whose git history should be kept."

git filter-branch --force --index-filter \
  "git rm  --ignore-unmatch --cached -qr . ; \
  cat $PWD/keep-these.txt | xargs git reset -q \$GIT_COMMIT --" \
  --prune-empty --tag-name-filter cat -- --all
echo "Cleaned up git history."

rm -rf .git/refs/original/ && \
git reflog expire --expire=now --all && \
git gc --aggressive --prune=now && \
rm keep-these.txt
echo "Cleaned up git internals."

git mv -v $PROJ/* .
rmdir $PROJ
MSG="Moved $PROJ files to top-level."
git commit -a -m "$MSG"
echo "$MSG"

git remote set-url origin git@github.com:nasa-cmr/${GIT_REPO}.git
git push origin master -f
echo "Pushed extracted code for $GIT_REPO to its own remote repo."

echo "Done."
