

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
echo "Running ßerver unit tests"
cd server
sh test.sh
if [ "$?" -ne "0" ]; then
  echo "Server unit tests failed"
  exit 1
fi
cd -
echo "Server unit tests succeeded"


function check_expected_outcome {
  # The test suite is intentionally constructed with 1 failure so that
  # we can detect that assertions actually are performed.
  if grep -q "Pass 6/7 tests" test_log; then
    echo "Pass.."
    rm test_log
    # exit;
  else
    echo "Expected string not found in test_log"
    rm test_log
    exit 1
  fi
}

echo "Running tests three times with different configurations.."

# Normal, synchronous test run
java -Dcpr.testDir=addnum/tests -jar code-prober-dev.jar --test addnum/AddNum.jar 2>/dev/null > test_log
check_expected_outcome

# Single worker process
java -Dcpr.testDir=addnum/tests -jar code-prober-dev.jar --test --concurrent=1 addnum/AddNum.jar 2>/dev/null> test_log
check_expected_outcome

# Multiple worker processes
java -Dcpr.testDir=addnum/tests -jar code-prober-dev.jar --test --concurrent=5 addnum/AddNum.jar 2>/dev/null> test_log
check_expected_outcome

echo "All three pass!"
