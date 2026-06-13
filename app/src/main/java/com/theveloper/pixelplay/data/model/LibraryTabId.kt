package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.R

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    val stringResId: Int,
    val defaultSort: SortOption
) {
    SONGS("SONGS", R.string.tab_songs, SortOption.SongTitleAZ),
    ALBUMS("ALBUMS", R.string.tab_albums, SortOption.AlbumTitleAZ),
    ARTISTS("ARTIST", R.string.tab_artists, SortOption.ArtistNameAZ),
    PLAYLISTS("PLAYLISTS", R.string.tab_playlists, SortOption.PlaylistNameAZ),
    FOLDERS("FOLDERS", R.string.tab_folders, SortOption.FolderNameAZ),
    LIKED("LIKED", R.string.tab_liked, SortOption.LikedSongDateLiked);

    companion object {
        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == key } ?: SONGS
    }
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == this }