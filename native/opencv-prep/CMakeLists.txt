cmake_minimum_required(VERSION 3.22.1)

set(OPENCV_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/opencv)

add_library(opencv_prep SHARED
    src/OpencvPrep.cpp
)

target_include_directories(opencv_prep PUBLIC
    include
    ${OPENCV_ROOT}/include
)

target_link_libraries(opencv_prep
    android
    log
)
