#!/bin/bash
set -e

echo "Generating Gradle Wrapper..."
# Use the system-installed gradle to generate the local wrapper binaries
gradle wrapper --gradle-version 8.7

echo "Granting execution permissions to the generated wrapper..."
chmod +x gradlew

echo "Gradle Wrapper setup complete."
