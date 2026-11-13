cmake_minimum_required(VERSION 3.22.1)

add_library(office_bridge SHARED
    src/OfficeBridge.cpp
)

target_include_directories(office_bridge PUBLIC
    include
)

target_link_libraries(office_bridge
    android
    log
)
