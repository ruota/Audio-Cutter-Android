cmake_minimum_required(VERSION 3.22.1)

project(audio_cutter_lame)

set(LAME_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/include")
set(LAME_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
set(LAME_ALT_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/jniLibs/${ANDROID_ABI}")

add_library(lamejni SHARED lame_jni.cpp)
target_link_options(lamejni PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(lamejni log)

set(LAME_HEADER "${LAME_INCLUDE_DIR}/lame/lame.h")
if (NOT EXISTS "${LAME_HEADER}")
    set(LAME_HEADER "${LAME_INCLUDE_DIR}/lame.h")
endif()

set(LAME_LIB_PATH "")
if (EXISTS "${LAME_LIB_DIR}/libmp3lame.so")
    set(LAME_LIB_PATH "${LAME_LIB_DIR}/libmp3lame.so")
elseif (EXISTS "${LAME_ALT_LIB_DIR}/libmp3lame.so")
    set(LAME_LIB_PATH "${LAME_ALT_LIB_DIR}/libmp3lame.so")
endif()

if (LAME_LIB_PATH AND EXISTS "${LAME_HEADER}")
    add_library(mp3lame SHARED IMPORTED)
    set_target_properties(mp3lame PROPERTIES
        IMPORTED_LOCATION "${LAME_LIB_PATH}")
    target_include_directories(lamejni PRIVATE "${LAME_INCLUDE_DIR}")
    target_link_libraries(lamejni mp3lame)
    target_compile_definitions(lamejni PRIVATE LAME_AVAILABLE=1)
endif()
