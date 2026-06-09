cmake_minimum_required(VERSION 3.22.1)
project("hardware_inference_engine")

set(CMAKE_CXX_STANDARD 17)

# Explicitly find the Android Neural Networks library (NNAPI)
find_library(nn_lib neuralnetworks)
find_library(log-lib log)

if(NOT nn_lib)
    message(FATAL_ERROR "NNAPI (neuralnetworks) library not found in NDK sysroot. Check minSdk version.")
endif()

add_library(
    hardware_inference_engine
    SHARED
    hardware_inference_engine.cpp
)

# Link using the found path variables
target_link_libraries(
    hardware_inference_engine
    ${log-lib}
    ${nn_lib}
    atomic
    m
)
