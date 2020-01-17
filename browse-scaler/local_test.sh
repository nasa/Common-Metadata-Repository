#!/bin/bash
CMR_ROOT="${CMR_ROOT:-cmr.earthdata.nasa.gov}"
CMR_ENVIRONMENT="${CMR_ENVIRONMENT:-prod}"

if [[ -f $1 ]]; then
    cd src
    docker run --network=host --rm -e REDIS_URL=docker.for.mac.host.internal -e CMR_ROOT=$CMR_ROOT -e CMR_ENVIRONMENT=$CMR_ENVIRONMENT -v "$PWD":/var/task lambci/lambda:nodejs12.x index.handler "$( < ../$1 )"
else
    echo "Usage: ./local_test.sh <json event file>"
fi
