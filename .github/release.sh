#!/usr/bin/env bash

set -e

CPR_VERSION="${GITHUB_REF/refs\/tags\//}" ./build-and-test.sh

UPLOAD_URL="${ASSETS_URL/api.github.com/uploads.github.com}"

# echo "Some variables for debugging, uncomment in case of issues:"
# echo "sha: $GITHUB_SHA"
# echo "ref: $GITHUB_REF"
# echo "ctx: $GITHUB_CONTEXT"
# echo "asu: $ASSETS_URL"
# echo "upl: $UPLOAD_URL"

if [ ! -f codeprober.jar ]; then
  echo "Missing codeprober.jar. Did the build silently fail?"
  exit 1
fi

curl -L \
  -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  -H "Content-Type: application/octet-stream" \
  "$UPLOAD_URL?name=codeprober.jar" \
  --data-binary "@codeprober.jar"

