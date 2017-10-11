#!/bin/bash
lein uberjar
docker build -t indexer .
docker run indexer
