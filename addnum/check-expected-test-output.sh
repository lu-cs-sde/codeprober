#!/bin/sh

# tail to remove startup message (Starting server, version ...)
java \
  -Dcpr.workspace=workspace \
  -Dcpr.workspaceFilePattern="ts/.*" \
  -jar ../codeprober.jar \
  --test AddNum.jar \
  | tail -n +2 \
  > actual_test_output.txt

diff workspace/expected_test_output.txt actual_test_output.txt
if [ "$?" -ne "0" ]; then
  echo "Unexpected test output, see diff (expected vs actual) output above"
  exit 1
fi
