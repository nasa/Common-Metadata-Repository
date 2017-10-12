#!/bin/bash

if [[ $1 == "separate" ]]; then
    cd ../../cubby-app
    sh build-cubby-for-container.sh &

    cd ../metadata-db-app
    sh build-metadata-db-for-container.sh &

    cd ../access-control-app
    sh build-access-control-for-container.sh &

    cd ../search-app
    sh build-search-app-for-container.sh &

    cd ../bootstrap-app
    sh build-bootstrap-for-container.sh &

    cd ../virtual-product-app
    sh build-virtual-product-for-container.sh &

    cd ../index-set-app
    sh build-index-set-for-container.sh &

    cd ../indexer-app
    sh build-indexer-for-container.sh &

    cd ../ingest-app
    sh build-ingest-for-container.sh &

    cd ../dev-system
fi

if [[ $1 == "together" || -z $1 ]]; then
  lein uberjar
  docker build -t dev-system .
  docker run -p 2999:2999 -p 3001:3001 -p 3002:3002 -p 3003:3003 -p 3004:3004 -p 3005:3005 -p 3006:3006 -p 3007:3007 -p 3008:3008 -p 3009:3009 -p 3010:3010 -p 3011:3011 -p 9210:9210 dev-system
  exit 0
fi
