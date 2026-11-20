package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.text.style.TextAlign

/**
 * 歌词行文本方向检测（RTL 支持，KI：RTL 歌词布局方向）。
 *
 * 按 Unicode 首强字符启发式（FIRSTSTRONG）判断行方向：
 * 第一个强方向字符为 R / AL（阿拉伯、希伯来、波斯、乌尔都等）→ RTL；
 * 为 L → LTR；整行无强方向字符（全符号 / 数字 / 空白）→ Neutral，跟随调用方默认。
 *
 * 注意：Compose / TextView 内部的 Unicode 双向算法只保证**字符书写顺序**正确；
 * 行的**对齐与排列方向**必须由调用方按此检测结果显式切换。
 */
object LyricsRtlDetector {

    enum class Direction { Rtl, Ltr, Neutral }

    fun directionality(text: String?): Direction {
        if (text.isNullOrBlank()) return Direction.Neutral
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT
                -> return Direction.Rtl

                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
                -> return Direction.Ltr

                else -> {}
            }
        }
        return Direction.Neutral
    }

    /** 行是否按 RTL 布局（Neutral 不算 RTL，交给调用方默认）。 */
    fun isRtl(text: String?): Boolean = directionality(text) == Direction.Rtl
}

/**
 * 按用户对齐设置与行内容方向解析该行的 [TextAlign]：
 * 「左对齐（默认）」设置下 RTL 行自动切换为 Right；显式 center / right 设置始终尊重用户选择。
 */
fun lyricLineTextAlign(lyricsAlignment: String, lineText: String?): TextAlign {
    return when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> if (LyricsRtlDetector.isRtl(lineText)) TextAlign.Right else TextAlign.Left
    }
}
