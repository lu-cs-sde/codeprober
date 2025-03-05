#!/bin/bash

set -e

if [ ! -f "setup.sh" ]; then
  echo "Missing 'setup.sh'. Copy 'setup.sh.example' to 'setup.sh' and fill in with your own configuration"
  exit 1
fi

echo "Loading aws commands and variables"
source setup.sh

if [ "$S3_BUCKET_NAME" = "" ]; then
  echo "Missing 'S3_BUCKET_NAME' in setup.sh"
  exit 1
fi
if [ "$SRC_WEBSITE_DIR" = "" ]; then
  echo "Missing 'SRC_WEBSITE_DIR' in setup.sh"
  exit 1
fi
if [ "$CLOUDFRONT_DISTRIBUTION" = "" ]; then
  echo "Missing 'CLOUDFRONT_DISTRIBUTION' in setup.sh"
  exit 1
fi

# This script uses the inverse of the traditional "dry-run" approach. Everything is dry-run by default, you must opt-in to a real ("wet") run.
WETNESS="--dryrun"
if [ "$WET_RUN" = "yes" ]; then
  WETNESS=""
  echo "Publishing!"
else
  echo "Doing dry-run. Set WET_RUN=yes to really publish"
fi


aws s3 sync $WETNESS --delete $SRC_WEBSITE_DIR s3://$S3_BUCKET_NAME
echo "S3 sync done"

if [ "$WET_RUN" = "yes" ]; then
  echo "Performing CloudFront invalidation"
  aws cloudfront create-invalidation --distribution-id $CLOUDFRONT_DISTRIBUTION --paths "/*"
fi
