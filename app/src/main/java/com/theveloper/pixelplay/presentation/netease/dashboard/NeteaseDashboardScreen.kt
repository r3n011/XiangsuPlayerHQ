package com.theveloper.pixelplay.presentation.netease.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.database.NeteasePlaylistEntity
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeteaseDashboardScreen(
    viewModel: NeteaseDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.screen_netease_dashboard_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.auth_cd_back))
                    }
                },
                actions = {
                    // Sync button
                    IconButton(
                        onClick = { viewModel.syncAllPlaylistsAndSongs() },
                        enabled = !isSyncing
                    ) {
                        Icon(
                            Icons.Rounded.CloudSync,
                            contentDescription = stringResource(R.string.cd_sync_all_playlists),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    // Logout button
                    IconButton(onClick = {
                        viewModel.logout()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.cd_logout)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sync status banner
            AnimatedVisibility(
                visible = syncMessage != null,
                enter = slideInVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = fadeOut()
            ) {
                syncMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.contains("failed"))
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded
                            )
                        }
                    }
                }
            }

            // User info header
            viewModel.userNickname?.let { nickname ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            if (viewModel.userAvatar != null) {
                                SmartImage(
                                    model = viewModel.userAvatar,
                                    contentDescription = nickname,
                                    contentScale = ContentScale.Crop,
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = nickname.firstOrNull()?.toString() ?: "N",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = nickname,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.dash_playlists_synced_count, playlists.size),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playlists header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dash_title_playlists),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
                if (playlists.isEmpty()) {
                    TextButton(onClick = { viewModel.syncAllPlaylistsAndSongs() }) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dash_action_sync), fontFamily = GoogleSansRounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Playlist list
            if (playlists.isEmpty() && !isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.dash_playlists_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.dash_playlists_empty_hint_subsonic),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = playlists,
                        key = { it.id }
                    ) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onSyncClick = { viewModel.syncPlaylistSongs(playlist.id) },
                            onDeleteClick = { viewModel.deletePlaylist(playlist.id) },
                            onClick = { viewModel.loadPlaylistSongs(playlist.id) },
                            cardShape = cardShape,
                            isSyncing = isSyncing
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: NeteasePlaylistEntity,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist cover
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverUrl != null) {
                    SmartImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.dash_song_count, playlist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sync button
            IconButton(
                onClick = onSyncClick,
                enabled = !isSyncing
            ) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = stringResource(R.string.cd_sync),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.cd_remove),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
