#!/bin/bash

set -e

# === Configuration ===
JAR_PATH="client/build/libs/client-all.jar"

# === Build ===
echo "Cleaning and building client..."
./gradlew client:clean
./gradlew client:build

# === Start Client ===
echo "Starting client..."
java -jar $JAR_PATH "$@"
