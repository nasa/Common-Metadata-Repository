#!/bin/bash
# Build and run the CMR as a single dev-system or as a set of microservices
# Parameters:
#  - separate: build and run each microservice separately
#  - together: build and run dev-system in a container
#  - default: pass no parameters, and the script will build and run dev-system

build-and-run-container () {
  lein uberjar
  docker build -t $1 .
  docker run -d $1 -p $2:$2
}

if [[ $1 == "separate" ]]; then
    cd ../../cubby-app
    build-and-run-container "cubby" 3007

    cd ../metadata-db-app
    build-and-run-container "metadata-db" 3001

    cd ../access-control-app
    build-and-run-container "access-control" 3011

    cd ../search-app
    build-and-run-container "search" 3003

    cd ../bootstrap-app
    build-and-run-container "bootstrap" 3006

    cd ../virtual-product-app
    build-and-run-container "virtual-product" 3009

    cd ../index-set-app
    build-and-run-container "index-set" 3005

    cd ../indexer-app
    build-and-run-container "indexer" 3004

    cd ../ingest-app
    build-and-run-container "ingest" 3002

    cd ../dev-system
fi

if [[ $1 == "together" || -z $1 ]]; then
  lein uberjar
  docker build -t dev-system .
  docker run -d -p 2999:2999 -p 3001:3001 -p 3002:3002 -p 3003:3003 -p 3004:3004 -p 3005:3005 -p 3006:3006 -p 3007:3007 -p 3008:3008 -p 3009:3009 -p 3010:3010 -p 3011:3011 -p 9210:9210 dev-system
  exit 0
fi
