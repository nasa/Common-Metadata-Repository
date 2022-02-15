#!/bin/bash
# builds node_modules, then zip up the source code
# for the browse-scaler to be read by the browse_scaler.tf file.
# see the `source_code_hash` field

(cd src && CURRENT_UID=$(id -u):$(id -u) docker-compose up --build)

(cd src && zip -r scaler.zip index.js cache.js cmr.js resize.js util.js config.js in-memory-cache.js package.json package-lock.json image-unavailable.svg node_modules)
