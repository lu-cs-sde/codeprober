#!/usr/bin/env bash

set -e

buildfile() {
  fname="$1"
  title="$2"
  bn="${fname#"../"}"
  ofile="public/${bn%.md}.html"
  case $bn in
    "README.md") ofile="public/index.html" ;;
    *) ;;
  esac
  mkdir -p "$(dirname "$ofile")"
  cssdir=""
  if [[ "$bn" == *"/"* ]]; then
    cssdir="../"
  fi
  echo "Time to build $bn css:$cssdir"
  pandoc "$fname" -o "$ofile" \
    --toc --standalone \
    --metadata title="$title" \
    -V basedir="$cssdir" -M document-css=false \
    --lua-filter=links-to-html.lua \
    --template=pandoc_template.html
}
buildfile ../README.md "Docs"

# Usage
buildfile ../usage/download_and_run.md "Download & Run"
buildfile ../usage/features.md "Features"
buildfile ../usage/troubleshooting.md "Troubleshooting"

# Config
buildfile ../config/ast_api.md "AST API"
buildfile ../config/environment_variables.md "Environment Variables"
buildfile ../config/system_properties.md "System Properties"

# Development
buildfile ../development/building.md "Building"

rsync -r  --progress ../media/ public/media

echo "Done!"
