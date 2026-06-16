cmake_minimum_required(VERSION 3.22.1)
project("hardware_inference_engine")

set(CMAKE_CXX_STANDARD 17)

find_library(log-lib log)

add_library(
    hardware_inference_engine
    SHARED
    hardware_inference_engine.cpp
)

target_link_libraries(
    hardware_inference_engine
    ${log-lib}
    atomic
    m
)
