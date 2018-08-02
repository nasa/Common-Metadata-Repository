#!/bin/bash

source collections.sh

# Authenticate
curl -i -XPOST -H "Content-Type: application/json" -H "Accept: application/json" http://localhost:3008/tokens -d ' {"token": {"username":"asfuser", "password":"topsecret", "client_id":"asftest", "user_ip_address":"127.0.0.1", "group_guids":["guid1", "guid2"]}}'
curl -i -H "Accept: application/json" -H "Echo-Token: foo" http://localhost:3008/tokens/ABC-1/token_info
 \
curl -i -XPUT -H "Content-type: application/echo10+xml" -H "Echo-Token: ABC-1" http://localhost:3002/providers/PROV1/collections/sampleNativeId15 -d \
"$COLLECTIONS"