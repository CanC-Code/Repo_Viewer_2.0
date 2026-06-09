#!/bin/bash
set -e

echo "Generating Gradle Wrapper (Version 8.7)..."
# Force the system to download the 8.7 binaries rather than falling back to the host default
gradle wrapper --gradle-version 8.7 --distribution-type bin

echo "Granting execution permissions to the generated wrapper..."
chmod +x gradlew

echo "Gradle Wrapper setup complete."
