#!/bin/bash
# Runs tests in docker and saves output as junit.xml

(cd src && docker-compose -f docker-compose.test.yml run --rm browse-scaler-cicd)
