package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlayerProgressStyle
import com.theveloper.pixelplay.presentation.components.PlayerThumbStyle
import com.theveloper.pixelplay.presentation.components.StyledPlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme

/**
 * ⚡ 自定义播放进度条设置页：轨道样式（9 种）+ 滑块样式（10 种）+ 播放时滑块旋转。
 * 交互与版式对齐 Rhythm 的播放器自定义页，写入统一由 Apply 提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProgressStyleSettingsScreen(
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val prefs by settingsViewModel.playerProgressPreferences.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    // ⚡ 进度条预览跟随播放器取色（专辑封面取色），而非整个应用的主题色。
    //   无歌曲 / 无封面取色时回退到应用主题色。
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val albumSchemePair by playerViewModel.currentAlbumArtColorSchemePair.collectAsStateWithLifecycle()
    val previewScheme = remember(albumSchemePair, isDarkTheme, scheme) {
        albumSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light } ?: scheme
    }
    val previewGradientColors = listOf(
        previewScheme.primary,
        previewScheme.secondary,
        previewScheme.tertiary
    )

    var pendingStyle by remember { mutableStateOf(PlayerProgressStyle.fromStorage(prefs.style)) }
    var pendingThumb by remember { mutableStateOf(PlayerThumbStyle.fromStorage(prefs.thumbStyle)) }
    var pendingRotate by remember { mutableStateOf(prefs.rotateThumb) }
    var previewValue by remember { mutableFloatStateOf(0.62f) }

    LaunchedEffect(prefs.style) {
        pendingStyle = PlayerProgressStyle.fromStorage(prefs.style)
    }
    LaunchedEffect(prefs.thumbStyle) {
        pendingThumb = PlayerThumbStyle.fromStorage(prefs.thumbStyle)
    }
    LaunchedEffect(prefs.rotateThumb) {
        pendingRotate = prefs.rotateThumb
    }

    val hasPendingChanges =
        pendingStyle.storageKey != prefs.style ||
            pendingThumb.storageKey != prefs.thumbStyle ||
            pendingRotate != prefs.rotateThumb

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding =
        bottomInset + if (stablePlayerState.currentSong != null) MiniPlayerHeight + 12.dp else 16.dp

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            PlayerProgressStyleHeader(
                scheme = scheme,
                onBackClick = onBackClick,
                onApplyClick = {
                    settingsViewModel.setPlayerProgressPreferences(
                        style = pendingStyle,
                        thumbStyle = pendingThumb,
                        rotateThumb = pendingRotate
                    )
                },
                applyEnabled = hasPendingChanges
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = scheme.surfaceContainer,
                shape = RoundedCornerShape(34.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(pendingStyle.labelResId),
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(pendingStyle.descriptionResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Surface(
                        color = scheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            StyledPlayerSeekBar(
                                value = { previewValue },
                                onValueChange = { previewValue = it },
                                onValueCommit = { previewValue = it },
                                style = pendingStyle,
                                thumbStyle = pendingThumb,
                                rotateThumbWhenPlaying = pendingRotate,
                                isPlaying = true,
                                activeTrackColor = previewScheme.primary,
                                inactiveTrackColor = previewScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                thumbColor = previewScheme.primary,
                                gradientColors = previewGradientColors,
                                semanticsLabel = stringResource(R.string.presentation_batch_f_progress_title)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.presentation_batch_f_progress_thumb_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = scheme.surfaceContainer,
                shape = RoundedCornerShape(34.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_f_progress_track_heading),
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_f_progress_track_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )

                    PlayerProgressStyle.entries.chunked(2).forEach { rowStyles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowStyles.forEach { entry ->
                                ProgressStylePreviewCard(
                                    selected = entry == pendingStyle,
                                    onClick = { pendingStyle = entry },
                                    label = stringResource(entry.labelResId),
                                    scheme = scheme,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    StyledPlayerSeekBar(
                                        value = { 0.6f },
                                        onValueChange = {},
                                        onValueCommit = {},
                                        style = entry,
                                        thumbStyle = PlayerThumbStyle.NONE,
                                        rotateThumbWhenPlaying = false,
                                        enabled = false,
                                        isPlaying = true,
                                        trackHeight = 4.dp,
                                        activeTrackColor = previewScheme.primary,
                                        inactiveTrackColor = previewScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                        gradientColors = previewGradientColors
                                    )
                                }
                            }
                            if (rowStyles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Surface(
                color = scheme.surfaceContainer,
                shape = RoundedCornerShape(34.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_f_progress_thumb_heading),
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_f_progress_thumb_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )

                    PlayerThumbStyle.entries.chunked(2).forEach { rowThumbs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowThumbs.forEach { entry ->
                                ProgressStylePreviewCard(
                                    selected = entry == pendingThumb,
                                    onClick = { pendingThumb = entry },
                                    label = stringResource(entry.labelResId),
                                    scheme = scheme,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    StyledPlayerSeekBar(
                                        value = { 0.6f },
                                        onValueChange = {},
                                        onValueCommit = {},
                                        style = PlayerProgressStyle.NORMAL,
                                        thumbStyle = entry,
                                        rotateThumbWhenPlaying = false,
                                        enabled = false,
                                        isPlaying = false,
                                        trackHeight = 4.dp,
                                        activeTrackColor = previewScheme.primary,
                                        inactiveTrackColor = previewScheme.onSurfaceVariant.copy(alpha = 0.24f),
                                        thumbColor = previewScheme.primary,
                                        gradientColors = previewGradientColors
                                    )
                                }
                            }
                            if (rowThumbs.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Surface(
                color = scheme.surfaceContainer,
                shape = RoundedCornerShape(34.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.presentation_batch_f_progress_rotate_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.presentation_batch_f_progress_rotate_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = pendingRotate,
                        onCheckedChange = { pendingRotate = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(contentBottomPadding))
        }
    }
}

@Composable
private fun PlayerProgressStyleHeader(
    scheme: ColorScheme,
    onBackClick: () -> Unit,
    onApplyClick: () -> Unit,
    applyEnabled: Boolean
) {
    Surface(color = scheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.surfaceContainerLow,
                    contentColor = scheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.presentation_batch_f_cd_close)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.presentation_batch_f_progress_title),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = onApplyClick,
                enabled = applyEnabled,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    disabledContainerColor = scheme.surfaceContainerHigh,
                    disabledContentColor = scheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.presentation_batch_f_apply),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ProgressStylePreviewCard(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentAlignment = Alignment.Center
            ) {
                preview()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
