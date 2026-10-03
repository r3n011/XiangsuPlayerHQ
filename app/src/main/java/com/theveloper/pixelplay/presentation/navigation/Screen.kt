package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Accounts : Screen("settings_accounts")
    object SourceMarket : Screen("source_market")
    object SettingsCategory : Screen("settings_category/{categoryId}?highlight={highlight}") {
        fun createRoute(categoryId: String, highlight: String? = null) =
            "settings_category/$categoryId" + (highlight?.let { "?highlight=${android.net.Uri.encode(it)}" } ?: "")
    }
    object PaletteStyle : Screen("palette_style_settings")
    object PlayerProgressStyle : Screen("player_progress_style_settings")
    /** 下载管理（下载队列 / 进行中 / 已完成） */
    object DownloadManager : Screen("download_manager")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }

    object DailyMixScreen : Screen("daily_mix")
    object AiMixScreen : Screen("ai_mix")
    object AiAssistant : Screen("ai_assistant")
    object DailyRecommendScreen : Screen("daily_recommend")
    object ToplistDetail : Screen("toplist_detail/{entryId}") {
        fun createRoute(entryId: String) = "toplist_detail/$entryId"
    }
    object RecentlyPlayed : Screen("recently_played")
    object Stats : Screen("stats")
    object GenreDetail : Screen("genre_detail/{genreId}") { // New screen
        fun createRoute(genreId: String) = "genre_detail/$genreId"
    }
    object DJSpace : Screen("dj_space")
    // La ruta base es "album_detail". La ruta completa con el argumento se define en AppNavigation.
    object AlbumDetail : Screen("album_detail/{albumId}") {
        // Función de ayuda para construir la ruta de navegación con el ID del álbum.
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }

    // 网易云歌手主页 - 通过 netease artist id 获取歌曲和专辑
    object ArtistHomepage : Screen("artist_homepage/{artistId}") {
        fun createRoute(artistId: Long) = "artist_homepage/$artistId"
    }

    object EditTransition : Screen("edit_transition?playlistId={playlistId}") {
        fun createRoute(playlistId: String?) =
            if (playlistId != null) "edit_transition?playlistId=$playlistId" else "edit_transition"
    }

    object About : Screen("about")
    object EasterEgg : Screen("easter_egg")

    object ArtistSettings : Screen("artist_settings")
    object DelimiterConfig : Screen("delimiter_config")
    object WordDelimiterConfig : Screen("word_delimiter_config")
    object ArtistWhitelistConfig : Screen("artist_whitelist_config")
    object Equalizer : Screen("equalizer")
    object DeviceCapabilities : Screen("device_capabilities")
    object NeteaseDashboard : Screen("netease_dashboard")
    object Messages : Screen("messages")
    object Chat : Screen("chat/{userId}?name={name}&avatar={avatar}") {
        fun createRoute(userId: Long, name: String, avatar: String?): String =
            "chat/$userId?name=${android.net.Uri.encode(name)}&avatar=${android.net.Uri.encode(avatar.orEmpty())}"
    }
    object QqMusicDashboard : Screen("qqmusic_dashboard")
    object NavidromeDashboard : Screen("navidrome_dashboard")
    object JellyfinDashboard : Screen("jellyfin_dashboard")
    object BilibiliFavorites : Screen("bilibili_favorites")
    object KugouDashboard : Screen("kugou_dashboard")

    object CloudMusicSettings : Screen("cloud_music_settings")
    object DotDeviceSettings : Screen("dot_device_settings")
    object Roaming : Screen("roaming_action")
    object Radio : Screen("radio")

    // ⚡ 听书（酷狗长音频）：书架 → 免费书库 / 搜索 → 专辑详情（章节播放）
    object Audiobook : Screen("audiobook")
    object AudiobookLibrary : Screen("audiobook_library")
    object AudiobookSearch : Screen("audiobook_search")
    object AudiobookAlbum :
        Screen("audiobook_album/{albumId}?title={title}&cover={cover}&author={author}&count={count}") {
        fun createRoute(
            albumId: String,
            title: String,
            coverUrl: String?,
            author: String?,
            chapterCount: Int,
        ): String =
            "audiobook_album/${android.net.Uri.encode(albumId)}" +
                "?title=${android.net.Uri.encode(title)}" +
                "&cover=${android.net.Uri.encode(coverUrl.orEmpty())}" +
                "&author=${android.net.Uri.encode(author.orEmpty())}" +
                "&count=$chapterCount"
    }
}
