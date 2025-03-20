#!/bin/sh
set -e

rm -rf build_tmp/
mkdir build_tmp

echo "Gathering sources.."
find src -name "*.java" > sources.txt

if [ ! -f "../codeprober.jar" ]; then
  echo "Missing codeprober.jar, it is needed to compile the protocol."
  echo "Please run the top build.sh script first, or download the latest release and place it in the root of this repo"
  exit 1
fi

echo "Building.."
javac @sources.txt -cp ../codeprober.jar -d build_tmp -source 8 -target 8

if [ "$(uname -a)" == "CYGWIN"* ]; then
	# Required for building on CYGWIN. Not sure if same is needed for WSL
	SEP=";"
else
	SEP=":"
fi

java \
  -DJAVA_DST_DIR="../server/src/codeprober/protocol/data/" \
  -DJAVA_DST_PKG="codeprober.protocol.data" \
  -DTS_DST_FILE="../client/ts/src/protocol.ts" \
  -cp "build_tmp$(echo $SEP)../codeprober.jar" protocolgen.GenAll

echo "Cleaning up.."
rm sources.txt
rm -rf build_tmp

echo "Done"
