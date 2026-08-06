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

# ============================================================
# AAudio 输出（Android O+ 低延迟原生音频）
# 作为 Media3 1.10.1 的自定义 AudioOutput 后端。
# 注意：AAudio 为 API 26+ 库，而项目 minSdk=23（构建平台 android-23），
# NDK 该平台目录没有 libaaudio.so，需直接链接 sysroot 下 API 26 的库。
# 运行时由系统提供 libaaudio.so（Android 8+ 设备镜像自带）。
# ============================================================
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(AAUDIO_TRIPLE "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(AAUDIO_TRIPLE "arm-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(AAUDIO_TRIPLE "x86_64-linux-android")
elseif(ANDROID_ABI STREQUAL "x86")
    set(AAUDIO_TRIPLE "i686-linux-android")
else()
    set(AAUDIO_TRIPLE "aarch64-linux-android")
endif()

# NDK 主机工具链目录（如 toolchains/llvm/prebuilt/windows-x86_64），用 GLOB 自动定位
file(GLOB NDK_LLVM_PREBUILT_DIRS ${ANDROID_NDK}/toolchains/llvm/prebuilt/*)
if(NOT NDK_LLVM_PREBUILT_DIRS)
    message(FATAL_ERROR "NDK llvm prebuilt dir not found under ${ANDROID_NDK}/toolchains/llvm/prebuilt")
endif()
list(GET NDK_LLVM_PREBUILT_DIRS 0 NDK_LLVM_PREBUILT_DIR)

set(AAUDIO_LIBRARY
        ${NDK_LLVM_PREBUILT_DIR}/sysroot/usr/lib/${AAUDIO_TRIPLE}/26/libaaudio.so)

if(NOT EXISTS ${AAUDIO_LIBRARY})
    message(FATAL_ERROR "libaaudio.so not found at ${AAUDIO_LIBRARY}")
endif()

add_library(
        aaudio_output
        SHARED
        aaudio_output.c
)

# 头文件按构建平台(android-23)把 API 26+ 函数标记 unavailable，
# 覆盖 __ANDROID_API__=26 使其可用（仅影响本 target 编译期）。
target_compile_definitions(aaudio_output PRIVATE __ANDROID_API__=26)

target_link_libraries(
        aaudio_output
        ${AAUDIO_LIBRARY}
        ${log-lib}
)
