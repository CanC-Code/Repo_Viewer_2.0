#!/bin/bash
set -e

CPP_DIR="app/src/main/cpp"

echo "Initializing download of ggml engine dependencies..."

# Ensure the native C++ directory exists
mkdir -p "$CPP_DIR"

# Fetch ggml.h (Header)
echo "Downloading ggml.h..."
curl -sL "https://raw.githubusercontent.com/ggerganov/ggml/master/include/ggml.h" -o "$CPP_DIR/ggml.h"

# Fetch ggml.c (Implementation)
echo "Downloading ggml.c..."
curl -sL "https://raw.githubusercontent.com/ggerganov/ggml/master/src/ggml.c" -o "$CPP_DIR/ggml.c"

echo "Success: ggml files placed in $CPP_DIR."
