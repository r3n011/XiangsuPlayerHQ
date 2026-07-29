cmake_minimum_required(VERSION 3.22.1)
project("miniaudio_jni")

add_library(
        miniaudio_jni
        SHARED
        miniaudio.c
        miniaudio_jni.c
)

find_library(
        log-lib
        log
)

target_link_libraries(
        miniaudio_jni
        ${log-lib}
)
