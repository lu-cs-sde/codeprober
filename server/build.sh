#!/bin/sh
set -e

rm -rf build_tmp/
mkdir build_tmp

echo "Gathering sources.."
find src -name "*.java" > sources.txt

echo "Building.."
javac @sources.txt -cp libs/json.jar -d build_tmp

unzip libs/json.jar '*.class' -x */* -d build_tmp
cd build_tmp

echo "Generating jar.."
echo "Main-Class: pasta.PastaServer" >> Manifest.txt
jar cfm ../../pasta-server.jar Manifest.txt **/*

cd ..

echo "Cleaning up.."
rm sources.txt
rm -rf build_tmp

echo "Done! Built 'pasta-server.jar'"
