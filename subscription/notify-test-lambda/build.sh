#!/bin/bash

# This script is used by the bamboo build project.

mkdir -p package

pip3 install --target ./package -r requirements.txt --no-compile

# Zip dependencies
cd package
zip -r ../notify_test_lambda_deployment_package.zip . -x "__pycache__/*"
cd ..

# Add contents of src directory to the zip file
cd src 
zip -r ../notify_test_lambda_deployment_package.zip . -x "__pycache__/*"

# Return to the top directory
cd ..

rm -r package

