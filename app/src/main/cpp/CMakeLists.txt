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

# ============================================================
# USB 独占输出（libusb）
# 预编译 libusb1.0.so 位于 app/src/main/jniLibs/${ANDROID_ABI}/，
# 头文件位于 app/src/main/cpp/libusb/include/。
# ============================================================
set(libusb_so_dir ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
set(libusb_h_dir ${CMAKE_SOURCE_DIR}/libusb/include)

include_directories(${libusb_h_dir})

# 导入预编译 libusb
add_library(libusb SHARED IMPORTED)
set_target_properties(libusb PROPERTIES IMPORTED_LOCATION ${libusb_so_dir}/libusb1.0.so)

# USB 音频输出 JNI 库（等时 OUT 端点写 PCM）
add_library(
        usbaudio_output
        SHARED
        usbaudio_output.c
)

target_link_libraries(
        usbaudio_output
        libusb
        ${log-lib}
)
