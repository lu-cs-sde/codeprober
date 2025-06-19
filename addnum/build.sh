#!/usr/bin/env bash
set -e

rm -rf build_tmp/
mkdir build_tmp

echo "Gathering sources.."
find src -name "*.java" > sources.txt

echo "Building.."
javac @sources.txt -d build_tmp -source 8 -target 8

cd build_tmp

echo "Generating jar.."
echo "Main-Class: addnum.AddNum" >> Manifest.txt

jar cfm ../AddNum.jar Manifest.txt **/*

cd ..

echo "Cleaning up.."
rm sources.txt
rm -rf build_tmp

echo "Done! Built 'AddNum.jar'"
