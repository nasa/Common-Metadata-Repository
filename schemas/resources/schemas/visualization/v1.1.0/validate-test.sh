#!/bin/bash

#set -e

source ~/gibs/tool/init.sh

smapleRecordFilePath=./metadata.json

echo "--------"
echo this should succeed
echo validating $x
check-jsonschema --schemafile schema.json $smapleRecordFilePath
