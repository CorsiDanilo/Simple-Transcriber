cmake_minimum_required(VERSION 3.10)

project(transcriber_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../../third_party/whisper.cpp)

file(READ "${WHISPER_LIB_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "unknown")
endif()

find_library(LOG_LIB log)

include(FetchContent)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

foreach(GGML_TARGET ggml ggml-base ggml-cpu)
    if (TARGET ${GGML_TARGET})
        target_compile_options(${GGML_TARGET} PRIVATE -O3)
    endif()
endforeach()

add_library(
    transcriber_whisper
    SHARED
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/jni.cpp
)

target_compile_definitions(transcriber_whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(transcriber_whisper PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")
target_compile_options(transcriber_whisper PRIVATE -O3)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(transcriber_whisper PRIVATE -march=armv8.2-a+fp16)
    target_compile_options(ggml PRIVATE -march=armv8.2-a+fp16)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(transcriber_whisper PRIVATE -mfpu=neon-vfpv4)
    target_compile_options(ggml PRIVATE -mfpu=neon-vfpv4)
endif()

if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(transcriber_whisper PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(transcriber_whisper PRIVATE -ffunction-sections -fdata-sections)
    target_link_options(transcriber_whisper PRIVATE -Wl,--gc-sections)
    target_link_options(transcriber_whisper PRIVATE -Wl,--exclude-libs,ALL)
    target_link_options(transcriber_whisper PRIVATE -flto)
endif()

target_include_directories(
    transcriber_whisper
    PRIVATE
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
)

target_link_libraries(transcriber_whisper ${LOG_LIB} android ggml)
