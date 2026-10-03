package com.theveloper.pixelplay.data.preferences

import androidx.annotation.StringRes
import com.theveloper.pixelplay.R

/**
 * 下载文件名模板（对齐参考实现的「歌名 / 歌手」占位思路，但只保留这三种常用组合）。
 *
 * 默认 [ARTIST_TITLE] 与旧行为一致（`歌手 - 歌名`），避免升级后老用户的文件名风格突变。
 */
object DownloadFileNameTemplate {
    /** `歌手 - 歌名`（默认） */
    const val ARTIST_TITLE = "artist_title"

    /** `歌名 - 歌手` */
    const val TITLE_ARTIST = "title_artist"

    /** 只有 `歌名` */
    const val TITLE = "title"

    @StringRes
    fun labelResId(value: String): Int = when (value) {
        TITLE_ARTIST -> R.string.setcat_download_file_name_title_artist
        TITLE -> R.string.setcat_download_file_name_title_only
        else -> R.string.setcat_download_file_name_artist_title
    }
}
