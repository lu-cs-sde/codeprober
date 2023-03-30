#!/bin/sh
set -e

LIBS=libs/json.jar:libs/junit-4.13.2.jar:libs/hamcrest-2.2.jar

## BUILD
rm -rf test_tmp/
mkdir test_tmp

echo "Gathering sources.."
find src-test src -name "*.java" > sources.txt

echo "Building.."
javac @sources.txt -cp $LIBS -d test_tmp -source 8 -target 8

## TEST
TEST_CLASSES=""
function addTest {
  CLS=$(echo $1 \
      | sed 's/^src-test\///' \
      | sed 's/\//\./g' \
      | sed 's/\.java$//' \
      )

  case $CLS in
    codeprober\.ast\.TestData|\
    codeprober\.ast\.ASTNodeAnnotation|\
    codeprober\.*FooTests)
      # Exclude from tests
      ;;

    *)
      TEST_CLASSES="$TEST_CLASSES $CLS"
  esac
}
for i in $(find src-test -name "*.java"); do [ -f "$i" ] && addTest "$i"; done

echo "Test classes: $TEST_CLASSES"
java -cp $LIBS:test_tmp org.junit.runner.JUnitCore $TEST_CLASSES
echo "All Tests pass successfully"
