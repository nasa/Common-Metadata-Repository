#!/bin/bash

SCRIPT_DIR=`dirname $0`

source $SCRIPT_DIR/functions.sh

REPO=cmr
PROJ=cmr
CONTINUE_FLAG=$1
START_REPO=cmr-nasa

clone-repo $START_REPO $REPO
cd $REPO

removal-check $CONTINUE_FLAG

find . -depth 1 \
	! -name ".git" \
	! -name ".gitignore" \
	! -name ".mailmap" \
	! -name "*.md" \
	! -name "LICENSE" \
	! -name "project.clj" \
	-exec rm -rfv {} \;
echo "Removed non-$PROJ files."

MSG="Removed sub-projects from new umbrella project."
git commit -a -m "$MSG"
echo "$MSG"

prune-repo `pwd`

LIBS="collection-renderer-lib orbits-lib oracle-lib elastic-utils-lib common-app-lib message-queue-lib acl-lib spatial-lib umm-spec-lib common-lib transmit-lib umm-lib"
APPS="index-set-app mock-echo-app cubby-app metadata-db-app indexer-app search-relevancy-test virtual-product-app access-control-app ingest-app bootstrap-app search-app"
OTHERS="search-relevancy-test system-int-test es-spatial-plugin vdd-spatial-viz dev-system"
git submodule add git@github.com:nasa-cmr/cli.git bin
for LIB in $LIBS;
	do git submodule add git@github.com:nasa-cmr/${LIB}.git ${LIB}
done
for APP in $APPS;
	GIT_REPO=`echo $APP|sed 's/-app$//'`
	do git submodule add git@github.com:nasa-cmr/${GIT_REPO}.git ${APP}
done
for OTHER in $OTHERS;
	do git submodule add git@github.com:nasa-cmr/${OTHER}.git $OTHER
done

MSG="Added all projects as submodules to CMR umbrella project."
git commit -a -m "$MSG"
echo "$MSG"

git remote set-url origin git@github.com:nasa-cmr/${REPO}.git
git push origin master -f
echo "Pushed extracted code for the CMR $REPO to its own remote repo."

echo "Done CMR umbrella project."
