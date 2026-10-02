package com.theveloper.pixelplay.presentation.components

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.SongRecognitionState

/**
 * 「听歌识曲」面板：录一段外放音乐 → 生成音频指纹 → 网易云匹配出歌曲。
 *
 * 状态由 [com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel.songRecognitionState] 驱动：
 * 空闲 → 点「开始识别」→ 录音中（正在聆听…）→ 结果列表 / 失败提示。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SongRecognitionSheet(
    state: SongRecognitionState,
    onStartRecognition: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.RECORD_AUDIO)
    )

    val startWithPermissionCheck: () -> Unit = {
        if (permissionState.allPermissionsGranted) {
            onStartRecognition()
        } else {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.song_recognition_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.song_recognition_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            when (state) {
                is SongRecognitionState.Listening -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.song_recognition_listening),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                is SongRecognitionState.Success -> {
                    Text(
                        text = stringResource(R.string.song_recognition_result_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.songs, key = { it.id }, contentType = { "recognized_song" }) { song ->
                            EnhancedSongListItem(
                                song = song,
                                isPlaying = false,
                                isCurrentSong = false,
                                showMoreOptionsButton = false,
                                showFavoriteButton = false,
                                onMoreOptionsClick = {},
                                onClick = { onPlaySong(song) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = startWithPermissionCheck) {
                        Text(stringResource(R.string.song_recognition_retry))
                    }
                }

                is SongRecognitionState.Failed -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = startWithPermissionCheck) {
                        Text(stringResource(R.string.song_recognition_retry))
                    }
                }

                SongRecognitionState.Idle -> {
                    Button(
                        onClick = startWithPermissionCheck,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.song_recognition_start),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (permissionState.allPermissionsGranted.not() &&
                        permissionState.shouldShowRationale
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.song_recognition_mic_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
