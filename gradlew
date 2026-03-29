#!/bin/sh
# Gradle wrapper script
SCRIPT_DIR=$(dirname "$0")
exec java -jar "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
