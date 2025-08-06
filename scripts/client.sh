#!/bin/bash

# sh scripts/client.sh --port=20000

set -e

# === Configuration ===
JAR_PATH="client/build/libs/client-all.jar"

# === Parse & Show Arguments ===
echo "Starting client with arguments: $@"

# === Build ===
echo "Cleaning and building client..."
./gradlew client:clean
./gradlew client:build

# === Start Client ===
echo "Launching client..."
java -jar "$JAR_PATH" "$@"
