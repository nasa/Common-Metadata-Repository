#!/bin/bash

#set -e

source ~/gibs/tool/init.sh

# check schema

echo this checks schema only
echo check-jsonschema --check-metaschema ./schema.json
check-jsonschema --check-metaschema schema.json
echo check-jsonschema --check-metaschema ./definitions.json
check-jsonschema --check-metaschema definitions.json
echo

echo this checks schema only
echo check-jsonschema --check-metaschema tiles/schema.json
check-jsonschema --check-metaschema tiles/schema.json
echo check-jsonschema --check-metaschema tiles/definitions.json
check-jsonschema --check-metaschema tiles/definitions.json
echo

echo this checks schema only
echo check-jsonschema --check-metaschema maps/schema.json
check-jsonschema --check-metaschema maps/schema.json
echo
