#!/bin/bash
lein uberjar
docker build -t ingest .
docker run ingest
