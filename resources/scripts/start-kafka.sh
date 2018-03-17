#!/bin/bash

KAFKA_REPO=https://github.com/hexagram30/kafka-docker.git
KAFKA_CLONE_DIR=kafka

if [[ ! -e "$KAFKA_CLONE_DIR" ]]; then
	git clone $KAFKA_REPO $KAFKA_CLONE_DIR
fi

cd $KAFKA_CLONE_DIR && \
	docker-compose up
