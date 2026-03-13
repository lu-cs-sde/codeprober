#!/usr/bin/env bash

set -e

echo "1+2 // [[Add.value=50]] [[Add.prettyPrint=\"123\"]]" > workspace/bless_test.addn

# tail to remove startup message (Starting server, version ...)
bless_output=$(java \
  -Dcpr.workspace=workspace \
  -Dcpr.workspaceFilePattern="bless_test\\.addn" \
  -Dcpr.permitImplicitStringConversion=false \
  -jar ../codeprober.jar \
  --test=bless AddNum.jar | tail -n 1)

if [ "$bless_output" != "Updated 2 probe(s) in 1 file(s)" ]; then
  echo "Unexpected bless output: $bless_output"
  exit 1
fi

after_state=$(cat workspace/bless_test.addn)

if [ "$after_state" != "1+2 // [[Add.value=3]] [[Add.prettyPrint=\"1 + 2\"]]" ]; then
  echo "Unexpected file contents after bless: $after_state"
  exit 1
fi

rm workspace/bless_test.addn

echo "Bless test OK"
