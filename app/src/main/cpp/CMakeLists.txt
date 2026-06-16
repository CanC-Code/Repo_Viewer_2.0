cmake_minimum_required(VERSION 3.22.1)
project("custom_hardware_engine")

# Enforce modern C++ standards for advanced memory management
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# Locate Android NDK system libraries
find_library(log-lib log)
find_library(android-lib android)

# Define the custom engine source
add_library(
    custom_hardware_engine
    SHARED
    hardware_inference_engine.cpp
    # Future inclusion: ggml.c
)

# Strict linkage ensuring functional correctness
target_link_libraries(
    custom_hardware_engine
    ${log-lib}
    ${android-lib}
    atomic
    m
)

# Optimization flags for mobile arm64-v8a compilation
target_compile_options(custom_hardware_engine PRIVATE -O3 -ffast-math -pthread)
