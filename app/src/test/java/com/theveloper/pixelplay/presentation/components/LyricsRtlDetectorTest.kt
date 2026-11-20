package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.text.style.TextAlign
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** RTL 歌词行方向检测（KI：RTL 歌词布局方向） */
class LyricsRtlDetectorTest {

    @Test
    fun arabic_isRtl() {
        assertThat(LyricsRtlDetector.isRtl("مرحبا بكم في الإذاعة")).isTrue()
    }

    @Test
    fun hebrew_isRtl() {
        assertThat(LyricsRtlDetector.isRtl("שלום עליכם")).isTrue()
    }

    @Test
    fun persian_isRtl() {
        assertThat(LyricsRtlDetector.isRtl("سلام دوست من")).isTrue()
    }

    @Test
    fun chinese_isLtr() {
        assertThat(LyricsRtlDetector.isRtl("你好，世界")).isFalse()
    }

    @Test
    fun english_isLtr() {
        assertThat(LyricsRtlDetector.isRtl("Hello, world!")).isFalse()
    }

    @Test
    fun neutralOnly_isNotRtl() {
        assertThat(LyricsRtlDetector.isRtl("12345 678 ... !!!")).isFalse()
    }

    @Test
    fun emptyOrNull_isNotRtl() {
        assertThat(LyricsRtlDetector.isRtl("")).isFalse()
        assertThat(LyricsRtlDetector.isRtl(null)).isFalse()
    }

    @Test
    fun firstStrongCharWins() {
        // 中文字符是 L 类：中文开头 + 阿语 → LTR
        assertThat(LyricsRtlDetector.isRtl("歌词 مرحبا")).isFalse()
        // 阿语开头 + 中文 → RTL
        assertThat(LyricsRtlDetector.isRtl("مرحبا 歌词")).isTrue()
    }

    @Test
    fun lyricLineTextAlign_followsUserSettingFirst() {
        val arabic = "مرحبا"
        // 显式 center / right 设置始终尊重用户选择
        assertThat(lyricLineTextAlign("center", arabic)).isEqualTo(TextAlign.Center)
        assertThat(lyricLineTextAlign("right", arabic)).isEqualTo(TextAlign.Right)
        assertThat(lyricLineTextAlign("right", "你好")).isEqualTo(TextAlign.Right)
    }

    @Test
    fun lyricLineTextAlign_defaultSwitchesForRtl() {
        val arabic = "مرحبا"
        // 「左对齐（默认）」设置下 RTL 行自动切右
        assertThat(lyricLineTextAlign("left", arabic)).isEqualTo(TextAlign.Right)
        assertThat(lyricLineTextAlign("left", "你好")).isEqualTo(TextAlign.Left)
        // 中性内容跟随默认（LTR）
        assertThat(lyricLineTextAlign("left", "123 ...")).isEqualTo(TextAlign.Left)
    }
}
