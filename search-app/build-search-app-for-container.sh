#!/bin/bash
lein uberjar
docker build -t search-app .
docker run search-app
