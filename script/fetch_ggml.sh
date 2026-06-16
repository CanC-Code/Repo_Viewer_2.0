#!/bin/bash
set -e

CPP_DIR="app/src/main/cpp/llama_cpp"

echo "Initializing shallow clone of core GGUF engine dependencies..."

# Clean previous allocations to avoid merge conflicts
rm -rf "$CPP_DIR"

# Perform a shallow clone of the latest llama.cpp core
git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "$CPP_DIR"

echo "Success: GGUF engine files placed in $CPP_DIR."
