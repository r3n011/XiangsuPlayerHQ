package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.FavoriteArtist
import com.theveloper.pixelplay.data.repository.FavoriteArtistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 收藏歌手 ViewModel
 *
 * 供歌手主页的收藏按钮与主页的收藏歌手卡片共用。
 */
@HiltViewModel
class FavoriteArtistViewModel @Inject constructor(
    private val repository: FavoriteArtistRepository
) : ViewModel() {

    /** 收藏的歌手列表（按收藏时间倒序） */
    val artists: StateFlow<List<FavoriteArtist>> = repository.artists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 已收藏的歌手 id 集合 */
    val favoriteIds: StateFlow<Set<Long>> = artists
        .map { list -> list.mapTo(LinkedHashSet()) { it.id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    fun toggleFavorite(id: Long, name: String, avatar: String = "", alias: String = "") {
        viewModelScope.launch {
            repository.toggle(
                FavoriteArtist(
                    id = id,
                    name = name,
                    avatar = avatar,
                    alias = alias
                )
            )
        }
    }
}
