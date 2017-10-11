#!/bin/bash
lein uberjar
docker build -t index-set .
docker run index-set
