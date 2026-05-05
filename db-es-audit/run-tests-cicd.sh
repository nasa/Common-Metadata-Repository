#!/bin/bash

export PYTHONPATH=src
export AWS_REGION="us-east-1"

pip3 install -r requirements.txt
python3 -m unittest discover -v -s ./test -p "*_test.py"
