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
jar cfm ../../pasta-server.jar Manifest.txt **/*

cd ..

echo "Cleaning up.."
rm sources.txt
rm -rf build_tmp

echo "Done! Built 'pasta-server.jar'"
