cmake_minimum_required(VERSION 3.22.1)
project("hardware_inference_engine")

# Require modern C++ standards for efficient memory and tensor manipulation
set(CMAKE_CXX_STANDARD 17)

# Define the shared library
add_library(
    hardware_inference_engine
    SHARED
    hardware_inference_engine.cpp
)

# Search for the standard Android logging library
find_library(log-lib log)

# Link the required libraries to your JNI bridge, specifically NNAPI
target_link_libraries(
    hardware_inference_engine
    ${log-lib}
    neuralnetworks
)
