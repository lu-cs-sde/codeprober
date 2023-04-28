#!/bin/sh
set -e

echo "Building protocol"
cd protocol
sh build.sh
cd -

echo "Building client"
cd client/ts
npm ci
npm run build
cd -

echo "Building server & CodeProber jar"
cd server
sh build.sh
cd -

echo "Done"
