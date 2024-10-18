#!/bin/sh
set -e

rm -rf build_tmp/
mkdir build_tmp

echo "Gathering sources.."
find src -name "*.java" > sources.txt

if [ ! -f "../CodeProber.jar" ]; then
  echo "Missing CodeProber.jar, it is needed to compile the protocol."
  echo "Please run the top build.sh script first, or download the latest release and place it in the root of this repo"
  exit 1
fi

echo "Building.."
javac @sources.txt -cp ../CodeProber.jar -d build_tmp -source 8 -target 8

java \
  -DJAVA_DST_DIR="../server/src/codeprober/protocol/data/" \
  -DJAVA_DST_PKG="codeprober.protocol.data" \
  -DTS_DST_FILE="../client/ts/src/protocol.ts" \
  -cp build_tmp:../CodeProber.jar protocolgen.GenAll

echo "Cleaning up.."
rm sources.txt
rm -rf build_tmp

echo "Done"
