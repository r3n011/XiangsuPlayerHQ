package com.theveloper.pixelplay.data.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件 ui.applySkin 的全局外观覆盖（实时生效）。
 *
 * 只覆盖强调色种子：主题仍走应用既有的 generateColorSchemeFromSeed，
 * 插件 reset 后自动回落到「跟随封面 / 壁纸」。
 */
@Singleton
class XsThemeOverlay @Inject constructor() {
    private val _accent = MutableStateFlow<String?>(null)
    val accent: StateFlow<String?> = _accent.asStateFlow()

    fun setAccent(hex: String?) {
        _accent.value = hex?.takeIf { it.matches(Regex("^#[0-9a-fA-F]{6}$")) }
    }

    fun reset() {
        _accent.value = null
    }
}
