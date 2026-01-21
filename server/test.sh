#!/usr/bin/env bash
set -e

if [[ "$(uname -a)" == "CYGWIN"* ]]; then
	# Required for building on CYGWIN. Not sure if same is needed for WSL
	SEP=";"
else
	SEP=":"
fi
LIBS="libs/json.jar$(echo $SEP)libs/junit-4.13.2.jar$(echo $SEP)libs/hamcrest-2.2.jar"

## BUILD
rm -rf test_tmp/
mkdir test_tmp

echo "Gathering sources.."
find src-test ../textprobe/src src -name "*.java" > sources.txt

echo "Building.."
javac @sources.txt -cp $LIBS -d test_tmp -source 8 -target 8

## TEST
TEST_CLASSES=""
addTest () {
  CLS=$(echo $1 \
      | sed 's/^src-test\///' \
      | sed 's/\//\./g' \
      | sed 's/\.java$//' \
      )

  case $CLS in
    codeprober\.ast\.TestData|\
    codeprober\.ast\.ASTNodeAnnotation|\
    codeprober\.JUnitTestRunnerWrapper|\
    codeprober\.ExistingTextProbeTest|\
    codeprober\.*RunDemoTests)
      # Exclude from tests
      ;;

    *)
      TEST_CLASSES="$TEST_CLASSES $CLS"
  esac
}
for i in $(find src-test -name "*.java"); do [ -f "$i" ] && addTest "$i"; done

echo "Test classes: $TEST_CLASSES"
java -cp "$LIBS$(echo $SEP)test_tmp" codeprober.JUnitTestRunnerWrapper $TEST_CLASSES
echo "All Tests pass successfully"
