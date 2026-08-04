package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.FavoriteArtist
import com.theveloper.pixelplay.data.netease.NeteaseArtistAvatarResolver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FavoriteArtistAvatarEntryPoint {
    fun neteaseArtistAvatarResolver(): NeteaseArtistAvatarResolver
}

/**
 * 解析收藏歌手头像：
 * - 已有头像（avatar）直接返回；
 * - 收藏时头像缺失的网易云歌手，按歌手名异步查询头像（进程内缓存，只解析一次）。
 */
@Composable
private fun rememberFavoriteArtistAvatar(artist: FavoriteArtist): String? {
    if (artist.avatar.isNotBlank()) return artist.avatar
    if (artist.name.isBlank()) return null

    val context = LocalContext.current
    val resolver = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            FavoriteArtistAvatarEntryPoint::class.java
        ).neteaseArtistAvatarResolver()
    }
    var resolved by remember(artist.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(artist.id, artist.name) {
        resolved = resolver.resolveAvatarUrl(artist.id, artist.name)
    }
    return resolved
}

/** 主页收藏歌手卡片的单个歌手项宽度 */
private val HomeFavoriteArtistCardWidth = 104.dp

/**
 * 主页「收藏的歌手」卡片
 *
 * 风格与主页其它卡片（Daily Mix / Recently Played）保持一致：
 * 标题行 + 横向滑动列表，点击歌手进入其网易云主页。
 */
@Composable
fun FavoriteArtistsSection(
    artists: List<FavoriteArtist>,
    onArtistClick: (FavoriteArtist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 20.dp),
            text = stringResource(R.string.home_favorite_artists_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items = artists, key = { it.id }) { artist ->
                FavoriteArtistCard(
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteArtistCard(
    artist: FavoriteArtist,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(HomeFavoriteArtistCardWidth),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 收藏时头像缺失的网易云歌手：异步解析头像，加载完成后自动显示
            val avatarUrl = rememberFavoriteArtistAvatar(artist)
            SmartImage(
                model = avatarUrl,
                contentDescription = null,
                targetSize = SmartImageListTargetSize,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}
