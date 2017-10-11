#!/bin/bash
lein uberjar
docker build -t metadata-db .
docker run metadata-db
