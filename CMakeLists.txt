cmake_minimum_required(VERSION 3.10.2)
project(native-lib)

if(DEFINED ANDROID_PLATFORM AND ANDROID_PLATFORM LESS 24)
    message(FATAL_ERROR "Vulkan is only supported on API 24 and above.")
endif()

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)
find_library(m-lib m)
find_library(vulkan-lib vulkan)
message(STATUS "Vulkan library found at: ${vulkan-lib}")

target_link_libraries(native-lib
        ${log-lib}
        ${m-lib}
        ${vulkan-lib}
)