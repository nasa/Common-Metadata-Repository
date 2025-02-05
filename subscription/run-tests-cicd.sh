#!/bin/bash

export PYTHONPATH=src

pip3 install boto3 Flask requests
#python3 -m unittest -v
python3 -m unittest discover -v -s ./test -p "*_test.py"
