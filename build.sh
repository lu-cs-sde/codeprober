#!/usr/bin/env bash
set -e

echo "Building dummy version of server, needed for protocol"
cd server
./build.sh
cd -

echo "Building protocol"
cd protocol
./build.sh
cd -

echo "Building client"
cd client/ts
npm ci
npm run build
cd -

echo "Building real server & CodeProber jar"
cd server
./build.sh
cd -

echo "Done"
