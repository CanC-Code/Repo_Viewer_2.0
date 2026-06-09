#!/bin/bash
set -e

echo "Generating Gradle Wrapper (Version 8.7)..."
# Downgrading to 8.7 to ensure compatibility with existing dependency syntax
gradle wrapper --gradle-version 8.7

echo "Granting execution permissions to the generated wrapper..."
chmod +x gradlew

echo "Gradle Wrapper setup complete."
