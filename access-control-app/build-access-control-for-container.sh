#!/bin/bash
lein uberjar
docker build -t access-control-app .
docker run access-control-app
