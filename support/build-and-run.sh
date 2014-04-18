#!/bin/sh
# This is a script for building and running the dev system for continuous integration.
# It will build and run it in the background.

lein clean
lein uberjar
nohup java -jar target/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar&