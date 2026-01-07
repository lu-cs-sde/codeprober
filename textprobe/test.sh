#!/usr/bin/env bash

set -e

./build.sh
DOG=true java  -Dcpr.verbose=true -Dcpr.workspace=workspace -jar ../codeprober.jar --test textprobe.jar
