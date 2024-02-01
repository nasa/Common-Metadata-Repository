#!/bin/bash

export PATH=\${PATH}:/build/bin
cmr build all

# Fetch JARs for running tests to speed up downstream jobs
lein modules with-profile dev,kaocha deps