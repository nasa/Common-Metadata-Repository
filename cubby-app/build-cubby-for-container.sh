#!/bin/bash
lein uberjar
docker build -t cubby .
docker run cubby
