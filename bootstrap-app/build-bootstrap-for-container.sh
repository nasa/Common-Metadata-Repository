#!/bin/bash
lein uberjar
docker build -t bootstrap .
docker run bootstrap
