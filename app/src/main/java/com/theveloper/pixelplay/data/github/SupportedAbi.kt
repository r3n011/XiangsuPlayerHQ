package com.theveloper.pixelplay.data.github

enum class SupportedAbi(
    val suffix: String,
    val displayName: String,
    val abiName: String
) {
    ARM64("-arm64.apk", "64 位", "arm64-v8a"),
    ARM32("-arm32.apk", "32 位", "armeabi-v7a");

    companion object {
        fun fromAssetName(name: String): SupportedAbi? {
            return entries.find { name.endsWith(it.suffix, ignoreCase = true) }
        }
    }
}
