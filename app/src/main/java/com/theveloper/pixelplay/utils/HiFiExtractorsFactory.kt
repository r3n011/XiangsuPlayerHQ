package com.theveloper.pixelplay.utils

import androidx.media3.extractor.ExtractorsFactory

class HiFiExtractorsFactory : ExtractorsFactory {
    override fun createExtractors(): Array<androidx.media3.extractor.Extractor> {
        return emptyArray()
    }
}