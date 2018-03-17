#!/bin/bash

docker run \
  -p 2181:2181 \
  -p 9092:9092 \
  -e ADVERTISED_HOST=`docker-machine ip \`docker-machine active\`` \
  -e ADVERTISED_PORT=9092 \
  spotify/kafka
