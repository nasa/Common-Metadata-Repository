#!/bin/bash
# Build and run the CMR as a single dev-system or as a set of microservices
# Parameters:
#  - separate: build and run each microservice separately
#  - together: build and run dev-system in a container
#  - APP_NAME: build and run just the given app, e.g., "cubby"
#  - NONE: pass no parameters, and the script will build and run dev-system

build-base () {
  cd ../common-app-lib
  docker build -t cmr-base .
  cd ../dev-system
}

build-and-run-container () {
  echo "Building $1 image ..."
  lein uberjar
  docker build -t $1 .
  docker run -d -p $2:$2 $1
}

clean-up () {
  echo "Cleaning up ..."
  cd ../ && rm -rf */target
}

build-base

if [[ $1 == "separate" ]]; then
    cd ../cubby-app
    build-and-run-container "cmr-cubby" 3007

    cd ../metadata-db-app
    build-and-run-container "cmr-metadata-db" 3001

    cd ../access-control-app
    build-and-run-container "cmr-access-control" 3011

    cd ../search-app
    build-and-run-container "cmr-search" 3003

    cd ../bootstrap-app
    build-and-run-container "cmr-bootstrap" 3006

    cd ../virtual-product-app
    build-and-run-container "cmr-virtual-product" 3009

    cd ../index-set-app
    build-and-run-container "cmr-index-set" 3005

    cd ../indexer-app
    build-and-run-container "cmr-indexer" 3004

    cd ../ingest-app
    build-and-run-container "cmr-ingest" 3002

    cd ../dev-system
elif [[ $1 == "together" || -z $1 ]]; then
  IMAGE_TAG=cmr-dev-system
  echo "Building $IMAGE_TAG image ..."
  lein uberjar
  docker build -t $IMAGE_TAG .
  docker run -d \
    -p 2999:2999 -p 3001:3001 -p 3002:3002 -p 3003:3003 -p 3004:3004 \
    -p 3005:3005 -p 3006:3006 -p 3007:3007 -p 3008:3008 -p 3009:3009 \
    -p 3010:3010 -p 3011:3011 -p 9210:9210 \
    $IMAGE_TAG
else
  cd ../${1}-app
  build-and-run-container "cmr-${1}" $2
fi

clean-up

