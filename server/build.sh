#!/usr/bin/env bash
set -e

rm -rf build_tmp/
mkdir build_tmp

echo "Gathering sources.."
find src ../textprobe/src -name "*.java" > sources.txt

echo "Building.."
if [[ "$(uname -a)" == "CYGWIN"* ]]; then
	# Required for building on CYGWIN. Not sure if same is needed for WSL
	SEP=";"
else
	SEP=":"
fi
# "Our own" class files
javac @sources.txt -cp "libs/json.jar$(echo $SEP)libs/junit-4.13.2.jar$(echo $SEP)libs/hamcrest-2.2.jar" -d build_tmp -source 8 -target 8
# Third party class files
unzip libs/json.jar '*.class' -x */* -d build_tmp
unzip libs/junit-4.13.2.jar '*.class' -x */* -d build_tmp
unzip libs/hamcrest-2.2.jar '*.class' -x */* -d build_tmp
# Resources
mkdir build_tmp/codeprober/resources/
cp -r ../client/public/* build_tmp/codeprober/resources/

cd build_tmp

echo "Generating jar.."
echo "Main-Class: codeprober.CodeProber" >> Manifest.txt

if [ -z "$CPR_VERSION" ]; then
  CPR_VERSION=$(git rev-parse --short HEAD)
  echo "Git-Version: $CPR_VERSION" >> cpr.properties
  if output=$(git status --porcelain) && [ -z "$output" ]; then
    echo "Git status is clean, this build can be released"
    echo "Git-Status: CLEAN" >> cpr.properties
  else
    echo "Git status is non-clean, this is a development build"
    echo "Git-Status: DIRTY" >> cpr.properties
  fi
else
  # CPR_VERSION explicitly set during build. This is a release build with custom tag.
  echo "Git-Version: $CPR_VERSION" >> cpr.properties
  echo "Git-Status: CLEAN" >> cpr.properties
fi

if hash date 2>/dev/null; then
  echo "Build-Time: $(date -u +%s)" >> cpr.properties
fi

DST=../../codeprober.jar
jar cfm $DST Manifest.txt cpr.properties **/*

cd ..

echo "Cleaning up.."
rm sources.txt
# rm -rf build_tmp

echo "Done! Built '$DST'"
