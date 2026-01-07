#!/usr/bin/env bash

set -e

./build.sh
java -Dcpr.workspace=workspace -jar ../codeprober.jar --test textprobe.jar
