cmake_minimum_required(VERSION 3.22.1)

project("openofficepdf_native" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 统一二进制安全与体积控制标志 (任务书 9.1 节)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections -Wall -Wextra")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden -ffunction-sections -fdata-sections -Wall -Wextra")

# 强制 16KB ELF 页面对齐 (Android 15+ 硬件合规与 Google Play 准入强制要求)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,--gc-sections")

# 包含各 Native 核心子目录
add_subdirectory(pdfium)
add_subdirectory(office-bridge)
add_subdirectory(ocr-runtime)
add_subdirectory(opencv-prep)
