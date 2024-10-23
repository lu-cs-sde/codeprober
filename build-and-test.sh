
echo "Building"
touch DUMMY_FILE_TO_FORCE_DEV_BUILD
sh build.sh
if [ "$?" -ne "0" ]; then
  rm DUMMY_FILE_TO_FORCE_DEV_BUILD
  echo "Build failed"
  exit 1
fi
rm DUMMY_FILE_TO_FORCE_DEV_BUILD

echo "Build success"

# -------------------------
# Test normal JUnit tests
cd server
sh test.sh
if [ "$?" -ne "0" ]; then
  echo "Server unit tests failed"
  exit 1
fi
cd -
echo "Server unit tests success"

# -------------------------
# Test running probe tests with "--test"
check_expected_outcome () {
  # The test suite is intentionally constructed with 1 failure so that
  # we can detect that assertions actually are performed.
  if grep -q "Pass 7/8 tests" test_log; then
    echo "Pass.."
    rm test_log
    # exit;
  else
    echo "Expected string not found in test_log"
    rm test_log
    exit 1
  fi
}

CPR_JAR="codeprober.jar"

echo "Running AddNum test suite three times with different configurations, using jar file: $CPR_JAR"

# Normal, synchronous test run
java -Dcpr.testDir=addnum/tests -jar $CPR_JAR --test addnum/AddNum.jar 2>/dev/null > test_log
check_expected_outcome

# Single worker process
java -Dcpr.testDir=addnum/tests -jar $CPR_JAR --test --concurrent=1 addnum/AddNum.jar 2>/dev/null> test_log
check_expected_outcome

# Multiple worker processes
java -Dcpr.testDir=addnum/tests -jar $CPR_JAR --test --concurrent=5 addnum/AddNum.jar 2>/dev/null> test_log
check_expected_outcome

echo "AddNum test suites success"

# -------------------------
# Test "--oneshot" capability
# List nodes at line 1 col 1 in a document containing "1+2+3"
java -jar $CPR_JAR --oneshot='{"type":"rpc","id":123,"data":{"type":"wsput:tunnel","session":"123","request":{"src":{"text":"1+2+3","posRecovery":"FAIL","cache":"FULL","tmpSuffix":".addnum"},"pos":4097,"type":"ListNodes"}}}' --output=oneshot_res addnum/AddNum.jar  2>/dev/null> test_log
# Check that the returned node listing is Num->Add->Add->Program as expected
actualNodeList=$(node -e 'console.log(JSON.stringify(JSON.parse(require("fs").readFileSync("oneshot_res")).data.value.response.nodes.map(n => n.result.type.split(".").slice(-1)[0])))')
expectedNodeList='["Num","Add","Add","Program"]'
if [ ! "$actualNodeList" = "$expectedNodeList" ]; then
  echo "Unexpected node list from --oneshot request"
  echo "Expected $expectedNodeList"
  echo "     Got $actualNodeList"
  exit 1
fi
echo "Oneshot tests success"
