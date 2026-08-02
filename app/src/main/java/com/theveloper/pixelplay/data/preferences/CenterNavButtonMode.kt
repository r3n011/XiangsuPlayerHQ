package com.theveloper.pixelplay.data.preferences

/**
 * 底部导航栏中间按钮的模式。
 *
 * - [DISCOVER]：显示「发现」按钮，点击后弹出选择（漫游 / 电台）；
 * - [ROAMING]：直接显示「漫游」按钮，点击立即进入漫游；
 * - [RADIO]：直接显示「电台」按钮，点击打开网络广播。
 */
enum class CenterNavButtonMode(val storageKey: String) {
    DISCOVER("discover"),
    ROAMING("roaming"),
    RADIO("radio"),
    NONE("none");

    companion object {
        fun fromStorageKey(key: String?): CenterNavButtonMode =
            entries.firstOrNull { it.storageKey == key } ?: DISCOVER
    }
}
