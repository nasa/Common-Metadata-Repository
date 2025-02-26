#!/bin/bash

export PYTHONPATH=src

pip3 install requests
python3 -m unittest discover -v -s ./test -p "*_test.py"
