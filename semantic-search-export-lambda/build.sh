#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
artifact_dir="${1:-$(mktemp -d)}"
if [[ -e "$artifact_dir" ]] && [[ -n "$(find "$artifact_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    echo "artifact directory must be empty: $artifact_dir" >&2
    exit 2
fi
mkdir -p "$artifact_dir"
python3 -m pip install --requirement "$project_dir/requirements.txt" --target "$artifact_dir"
cp -R "$project_dir/src/cmr_export" "$artifact_dir/"
(cd "$artifact_dir" && python3 -c 'from cmr_export.handler import lambda_handler; assert callable(lambda_handler)')
(cd "$artifact_dir" && zip -qr "$project_dir/semantic-search-export-lambda.zip" .)
echo "$project_dir/semantic-search-export-lambda.zip"
