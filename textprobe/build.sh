#!/usr/bin/env bash

set -e

rm -rf build_tmp
mkdir build_tmp


echo "Building.."
(
  find src -name "*.java" > sources.txt
  javac @sources.txt -d build_tmp -source 8 -target 8
)

echo "Generating jar.."
(
  cd build_tmp
  echo "Main-Class: codeprober.textprobe.TextProbe" >> Manifest.txt
  jar cfm ../textprobe.jar Manifest.txt **/*
)

echo "Cleaning up.."
rm sources.txt

echo "Done! Built 'textprobe.jar'"
