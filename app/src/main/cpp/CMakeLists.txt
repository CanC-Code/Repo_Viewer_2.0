cmake_minimum_required(VERSION 3.22.1)
project("custom_hardware_engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

find_library(log-lib log)
find_library(android-lib android)

# Include the fetched llama.cpp directory
add_subdirectory(llama_cpp)

# Define the custom JNI engine source
add_library(
    custom_hardware_engine
    SHARED
    hardware_inference_engine.cpp
)

# Strict linkage to ensure the GGUF models can be evaluated 
target_link_libraries(
    custom_hardware_engine
    llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

target_include_directories(custom_hardware_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/llama_cpp)
target_include_directories(custom_hardware_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/llama_cpp/common)

# Architecture optimization flags
target_compile_options(custom_hardware_engine PRIVATE -O3 -ffast-math -pthread)
