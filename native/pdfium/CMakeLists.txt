cmake_minimum_required(VERSION 3.22.1)

# PDFium 预编译库对接 (任务书 Phase 0 契约)
set(PDFIUM_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/pdfium)

add_library(pdfium_bridge SHARED
    src/PdfiumBridge.cpp
    src/PdfiumObjectEditor.cpp
    src/PdfiumJni.cpp
)

target_include_directories(pdfium_bridge PUBLIC
    include
    ${PDFIUM_ROOT}/include
)

target_link_libraries(pdfium_bridge
    android
    jnigraphics
    log
)

target_link_options(pdfium_bridge PRIVATE "-Wl,-z,max-page-size=16384")
