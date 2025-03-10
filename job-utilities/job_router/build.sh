#!/bin/bash
mkdir package

pip3 install --target ./package -r requirements.txt
cd package

zip -r ../deployment_package.zip .
cd ..

zip deployment_package.zip lambda_function.py