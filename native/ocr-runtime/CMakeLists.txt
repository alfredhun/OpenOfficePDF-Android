cmake_minimum_required(VERSION 3.22.1)

set(ONNX_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/onnxruntime)

add_library(ocr_runtime SHARED
    src/OcrRuntime.cpp
)

target_include_directories(ocr_runtime PUBLIC
    include
    ${ONNX_ROOT}/include
)

target_link_libraries(ocr_runtime
    android
    log
)
