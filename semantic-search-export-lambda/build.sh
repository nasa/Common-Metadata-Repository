#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
image_uri="${1:?usage: build.sh ECR_IMAGE_URI [linux/amd64|linux/arm64]}"
platform="${2:-linux/amd64}"
docker build --platform "$platform" -t "$image_uri" "$project_dir"
echo "$image_uri"
