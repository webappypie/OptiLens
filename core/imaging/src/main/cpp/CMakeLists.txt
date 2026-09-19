cmake_minimum_required(VERSION 3.22.1)

project("optilens_imaging" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compiler optimization flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -Wall -Wextra -Werror")

add_library(
    optilens_imaging
    SHARED
    scoring/FrameScorer.cpp
    alignment/PyramidalOpticalFlow.cpp
    alignment/GhostMaskEstimator.cpp
    fusion/TemporalFusionEngine.cpp
    fusion/ToneMapper.cpp
    fusion/ColorCorrector.cpp
    portrait/PortraitProcessor.cpp
    enhance/AiEnhanceProcessor.cpp
    sr/SuperResolutionProcessor.cpp
    jni/OptiLensImagingJni.cpp
)

target_include_directories(
    optilens_imaging
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/scoring
    ${CMAKE_CURRENT_SOURCE_DIR}/alignment
    ${CMAKE_CURRENT_SOURCE_DIR}/fusion
    ${CMAKE_CURRENT_SOURCE_DIR}/portrait
    ${CMAKE_CURRENT_SOURCE_DIR}/enhance
    ${CMAKE_CURRENT_SOURCE_DIR}/sr
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
    optilens_imaging
    ${log-lib}
    ${jnigraphics-lib}
    m
)
