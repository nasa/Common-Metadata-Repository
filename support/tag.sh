#!/bin/sh

if [ -z "$1" ]
  then
    echo "Must supply the tag name to use. ie. sprint10"
    exit 1
fi

cd ..

cd cmr-app-clojure-template
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-bootstrap-app
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-common-lib
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-dev-system
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-elastic-utils-lib
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-es-spatial-plugin
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-index-set-app
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-indexer-app
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-ingest-app
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-metadata-db-app
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-oracle-lib
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-search-app
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-spatial-lib
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-system-int-test
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-system-trace-lib
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-transmit-lib
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-umm-lib
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-vdd-spatial-viz
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-mock-echo-app
git tag -a $1 -m "Tagging at the end of the sprint"
git push --tags
cd ..

cd cmr-dev-system
