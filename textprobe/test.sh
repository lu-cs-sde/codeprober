#!/usr/bin/env bash

set -e

./build.sh
java -Dcpr.workspace=workspace \
      -Dcpr.workspaceFilePattern="(?!future_syntax).*" \
      -jar ../codeprober.jar --test textprobe.jar

java -Dcpr.workspace=workspace \
     -Dcpr.workspaceFilePattern="future_syntax.*" \
     -Dcpr.permitImplicitStringConversion=false \
     -jar ../codeprober.jar --test textprobe.jar
