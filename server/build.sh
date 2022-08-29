#!/bin/sh
set -e

rm -rf build_tmp/
mkdir build_tmp

echo "Gathering sources.."
find src -name "*.java" > sources.txt

echo "Building.."
# "Our own" class files
javac @sources.txt -cp libs/json.jar -d build_tmp
# Third party class files
unzip libs/json.jar '*.class' -x */* -d build_tmp
# Resources
cp -r src/pasta/resources build_tmp/pasta/

cd build_tmp

echo "Generating jar.."
echo "Main-Class: pasta.PastaServer" >> Manifest.txt


GITV=$(git rev-parse HEAD)
echo "git version: $GITV"
if output=$(git status --porcelain) && [ -z "$output" ]; then
  # Working directory clean
  GITS=""
else
  # Uncommitted changes
  GITS=" [DEV]"
fi

echo "git clean status: $GITS"

# if hash md5sum 2>/dev/null; then
#   find . -type f \( -not -name "md5sum.txt" \) -exec md5sum '{}' \; > md5sum.txt
#   echo "KL.SRC.VERSION=$(md5sum md5sum.txt | awk '{print $1}')" >> kl.properties
# elif hash md5 2>/dev/null; then
#   find . -type f \( -not -name "md5sum.txt" \) -exec md5 -q '{}' \; > md5sum.txt
#   echo "KL.SRC.VERSION=$(md5 -q md5sum.txt)" >> kl.properties
# else
#   echo "Must have md5sum or md5 installed to calculate "
#   exit 1
# fi


jar cfm ../../pasta-server.jar Manifest.txt **/*

cd ..

echo "Cleaning up.."
rm sources.txt
rm -rf build_tmp

echo "Done! Built 'pasta-server.jar'"
