#!/bin/bash
lein uberjar
docker build -t virtual-product .
docker run virtual-product
