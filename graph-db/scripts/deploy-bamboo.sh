#!/bin/bash

# Bail on unset variables, errors and trace execution
set -eux

# Set up Docker image
#####################

cat <<EOF > .dockerignore
node_modules
.serverless
EOF

cat <<EOF > Dockerfile
FROM node:16
COPY . /build
WORKDIR /build
RUN npm install
EOF

dockerTag=cmr-graph-db-$bamboo_ENVIRONMENT
docker build -t $dockerTag .

# Convenience function to invoke `docker run` with appropriate env vars instead of baking them into image
dockerRun() {
    docker run \
        -e "AWS_ACCESS_KEY_ID=$bamboo_AWS_ACCESS_KEY_ID" \
        -e "AWS_SECRET_ACCESS_KEY=$bamboo_AWS_SECRET_ACCESS_KEY" \
        -e "AWS_REGION=$bamboo_AWS_REGION" \
        -e "CMR_CONCEPT_SNS_TOPIC=$bamboo_CMR_CONCEPT_SNS_TOPIC" \
        $dockerTag "$@"
}

# Execute serverless commands in Docker
#######################################

stageOpts="--stage $bamboo_ENVIRONMENT"

echo 'Deploying CMR GraphDB...'
dockerRun npx serverless deploy $stageOpts --force
