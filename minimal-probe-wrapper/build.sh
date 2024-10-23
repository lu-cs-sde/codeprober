#!/bin/bash

set -e

# Clean up from previous build
rm -rf build_tmp

# Build
echo "Building sources"
javac -d build_tmp src/mpw/*.java -source 8 -target 8

# Make Jar
echo "Generating jar.."
cd build_tmp
echo "Main-Class: mpw.MinimalProbeWrapper" >> Manifest.txt
jar cfm ../my-minimal-wrapper.jar Manifest.txt **/*

# Cleanup
cd -
rm  -rf build_tmp

echo "Done, build my-minimal-wrapper.jar"
echo "Start with 'java -jar /path/to/codeprober.jar /path/to/my-minimial-wrapper.jar'"
