package com.theveloper.pixelplay.data.model

import androidx.annotation.StringRes
import com.theveloper.pixelplay.R

/**
 * 根据 SortOption 返回对应的字符串资源 ID
 * 用于在 UI 层获取本地化的显示名称
 */
@StringRes
fun SortOption.getDisplayNameResId(): Int {
    return when (this) {
        // Song Sort Options
        SortOption.SongCustomOrder -> R.string.sort_option_custom_order
        SortOption.SongDefaultOrder -> R.string.sort_option_default_order
        SortOption.SongTitleAZ -> R.string.sort_option_title_az
        SortOption.SongTitleZA -> R.string.sort_option_title_za
        SortOption.SongArtist -> R.string.sort_option_artist
        SortOption.SongArtistDesc -> R.string.sort_option_artist_desc
        SortOption.SongAlbum -> R.string.sort_option_album
        SortOption.SongAlbumDesc -> R.string.sort_option_album_desc
        SortOption.SongDateAdded -> R.string.sort_option_date_added
        SortOption.SongDateAddedAsc -> R.string.sort_option_date_added_oldest
        SortOption.SongDuration -> R.string.sort_option_duration
        SortOption.SongDurationAsc -> R.string.sort_option_duration_shortest
        
        // Album Sort Options
        SortOption.AlbumCustomOrder -> R.string.sort_option_custom_order
        SortOption.AlbumTitleAZ -> R.string.sort_option_title_az
        SortOption.AlbumTitleZA -> R.string.sort_option_title_za
        SortOption.AlbumArtist -> R.string.sort_option_artist
        SortOption.AlbumArtistDesc -> R.string.sort_option_artist_desc
        SortOption.AlbumReleaseYear -> R.string.sort_option_release_year
        SortOption.AlbumReleaseYearAsc -> R.string.sort_option_release_year_oldest
        SortOption.AlbumDateAdded -> R.string.sort_option_date_added
        SortOption.AlbumSizeAsc -> R.string.sort_option_fewest_songs
        SortOption.AlbumSizeDesc -> R.string.sort_option_most_songs
        
        // Artist Sort Options
        SortOption.ArtistCustomOrder -> R.string.sort_option_custom_order
        SortOption.ArtistNameAZ -> R.string.sort_option_name_az
        SortOption.ArtistNameZA -> R.string.sort_option_name_za
        SortOption.ArtistNumSongsDesc -> R.string.sort_option_number_of_songs_most
        SortOption.ArtistNumSongsAsc -> R.string.sort_option_number_of_songs_fewest
        
        // Playlist Sort Options
        SortOption.PlaylistCustomOrder -> R.string.sort_option_custom_order
        SortOption.PlaylistNameAZ -> R.string.sort_option_name_az
        SortOption.PlaylistNameZA -> R.string.sort_option_name_za
        SortOption.PlaylistDateCreated -> R.string.sort_option_date_created
        SortOption.PlaylistDateCreatedAsc -> R.string.sort_option_date_created_oldest
        
        // Liked Sort Options
        SortOption.LikedSongCustomOrder -> R.string.sort_option_custom_order
        SortOption.LikedSongTitleAZ -> R.string.sort_option_title_az
        SortOption.LikedSongTitleZA -> R.string.sort_option_title_za
        SortOption.LikedSongArtist -> R.string.sort_option_artist
        SortOption.LikedSongArtistDesc -> R.string.sort_option_artist_desc
        SortOption.LikedSongAlbum -> R.string.sort_option_album
        SortOption.LikedSongAlbumDesc -> R.string.sort_option_album_desc
        SortOption.LikedSongDateLiked -> R.string.sort_option_date_liked
        SortOption.LikedSongDateLikedAsc -> R.string.sort_option_date_liked_oldest
        
        // Folder Sort Options
        SortOption.FolderCustomOrder -> R.string.sort_option_custom_order
        SortOption.FolderNameAZ -> R.string.sort_option_name_az
        SortOption.FolderNameZA -> R.string.sort_option_name_za
        SortOption.FolderSongCountAsc -> R.string.sort_option_fewest_songs
        SortOption.FolderSongCountDesc -> R.string.sort_option_most_songs
        SortOption.FolderSubdirCountAsc -> R.string.sort_option_fewest_subfolders
        SortOption.FolderSubdirCountDesc -> R.string.sort_option_most_subfolders
        
        else -> R.string.sort_option_custom_order
    }
}

/**
 * 根据 SortOption 的 methodKey 返回对应的方法标签字符串资源 ID
 * 用于在 UI 层获取本地化的方法标签
 */
@StringRes
fun SortOption.getMethodLabelResId(): Int {
    return when (this.methodKey) {
        "song_title", "album_title", "liked_title" -> R.string.sort_option_title
        "song_artist", "album_artist", "liked_artist" -> R.string.sort_option_artist
        "song_album", "album_album", "liked_album" -> R.string.sort_option_album
        "song_date_added", "album_date_added" -> R.string.sort_option_date_added
        "song_duration" -> R.string.sort_option_duration
        "album_release_year" -> R.string.sort_option_release_year
        "album_size", "folder_song_count" -> R.string.sort_option_song_count
        "artist_name", "playlist_name", "folder_name" -> R.string.sort_option_name
        "artist_num_songs" -> R.string.sort_option_number_of_songs
        "playlist_date_created" -> R.string.sort_option_date_created
        "liked_date_liked" -> R.string.sort_option_date_liked
        "folder_subdir_count" -> R.string.sort_option_subfolder_count
        "song_custom_order", "album_custom_order", "artist_custom_order",
        "playlist_custom_order", "liked_song_custom_order", "folder_custom_order" -> R.string.sort_option_custom_order
        "song_default_order" -> R.string.sort_option_default_order
        else -> R.string.sort_option_custom_order
    }
}
