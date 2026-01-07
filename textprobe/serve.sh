#!/usr/bin/env bash


./build.sh
WEB_RESOURCES_OVERRIDE=../client/public PORT=8011 java -Dcpr.workspace=workspace -jar ../codeprober.jar textprobe.jar
# PORT=8001 java -Dcpr.workspace=workspace -jar ~/Downloads/codeprober\(7\).jar textprobe.jar

