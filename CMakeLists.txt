cmake_minimum_required(VERSION 3.10.2)
project(native-lib)

option(USE_VULKAN "Build with Vulkan support" OFF)

message(STATUS "USE_VULKAN = ${USE_VULKAN}")
message(STATUS "Target API level (CMAKE_SYSTEM_VERSION) = ${CMAKE_SYSTEM_VERSION}")

if(USE_VULKAN)
    if(CMAKE_SYSTEM_VERSION LESS 24)
        message(FATAL_ERROR "Vulkan is only supported on API 24 and above.")
    endif()

    find_library(vulkan-lib vulkan REQUIRED)
    message(STATUS "Vulkan library found at: ${vulkan-lib}")
endif()

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log REQUIRED)
find_library(m-lib m REQUIRED)

if(USE_VULKAN)
    target_compile_definitions(native-lib PRIVATE USE_VULKAN=1)
    target_link_libraries(native-lib
            ${log-lib}
            ${m-lib}
            ${vulkan-lib}
    )
else()
    target_link_libraries(native-lib
            ${log-lib}
            ${m-lib}
    )
endif()
