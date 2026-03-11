#!/usr/bin/env bash

set -e

./build.sh

doTest() {
  llParser=$1
  echo ""
  echo  "== LLParser=$llParser"
  echo "...implicit RHS"
  java -Dcpr.workspace=workspace \
      -Dcpr.workspaceFilePattern="legacy_syntax.*" \
      -Dcpr.permitImplicitStringConversion=true \
      -Dcpr.llTextProbeParser="$llParser" \
      -jar ../codeprober.jar --test textprobe.jar

  echo "...explicit RHS"
  java -Dcpr.workspace=workspace \
        -Dcpr.workspaceFilePattern="(?!legacy_syntax|ll_syntax).*" \
        -Dcpr.permitImplicitStringConversion=false \
        -Dcpr.llTextProbeParser="$llParser" \
        -jar ../codeprober.jar  --test textprobe.jar

}

doTest "false"
doTest "true"

echo "(LL+Explicit String)-Only syntax"
java -Dcpr.workspace=workspace \
        -Dcpr.workspaceFilePattern="ll_syntax.*" \
        -Dcpr.permitImplicitStringConversion=false \
        -Dcpr.llTextProbeParser="true" \
        -jar ../codeprober.jar  --test textprobe.jar
