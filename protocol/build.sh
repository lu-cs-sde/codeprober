#!/bin/sh
set -e

rm -rf build_tmp/
mkdir build_tmp

echo "Gathering sources.."
find src -name "*.java" > sources.txt

echo "Building.."
javac @sources.txt -cp ../code-prober-dev.jar -d build_tmp -source 8 -target 8


java \
  -DJAVA_DST_DIR="../server/src/codeprober/protocol/data/" \
  -DJAVA_DST_PKG="codeprober.protocol.data" \
  -DTS_DST_FILE="../client/ts/src/protocol.ts" \
  -cp build_tmp:../code-prober-dev.jar protocolgen.GenAll

echo "Cleaning up.."
rm sources.txt
rm -rf build_tmp

echo "Done"
