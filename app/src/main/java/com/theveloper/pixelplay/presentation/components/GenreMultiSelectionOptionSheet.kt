package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.presentation.components.subcomps.AutoSizingTextToFill
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreMultiSelectionOptionSheet(
    selectedGenres: List<Genre>,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = 200))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StackedGenrePreviews(
                    genres = selectedGenres.take(4)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    AutoSizingTextToFill(
                        text = stringResource(R.string.multi_selection_genres_count_upper, selectedGenres.size),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded,
                        minFontSize = 16.sp,
                        maxFontSizeLimit = 24.sp,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.multi_selection_genres_selected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = GoogleSansRounded,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.multi_selection_genres_queue_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenreSelectionActionButton(
                    onClick = {
                        onPlay()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.song_info_cd_play_all)
                        )
                    },
                    text = stringResource(R.string.song_info_action_play)
                )

                GenreSelectionActionButton(
                    onClick = {
                        onAddToPlaylist()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = stringResource(R.string.song_info_cd_add_to_playlist)
                        )
                    },
                    text = stringResource(R.string.common_playlist)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenreSelectionActionButton(
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(R.string.song_info_cd_queue_next)
                        )
                    },
                    text = stringResource(R.string.song_info_action_queue_next)
                )

                GenreSelectionActionButton(
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.song_info_cd_add_to_queue)
                        )
                    },
                    text = stringResource(R.string.song_info_action_add_to_queue)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GenreSelectionActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.ButtonColors,
    icon: @Composable () -> Unit,
    text: String
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 66.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = colors,
        shape = CircleShape
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        TightWrapText(
            text = text,
            modifier = Modifier.padding(end = 4.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun StackedGenrePreviews(
    genres: List<Genre>,
    modifier: Modifier = Modifier
) {
    val imageSize = 64.dp
    val overlap = 30.dp
    val borderWidth = 3.dp
    val borderColor = MaterialTheme.colorScheme.surfaceContainerLow
    val isDark = LocalPixelPlayDarkTheme.current

    val numGenres = genres.size
    val totalWidth = if (numGenres == 0) 0.dp else imageSize + (imageSize - overlap) * (numGenres - 1)

    Box(
        modifier = modifier.width(totalWidth),
        contentAlignment = Alignment.CenterStart
    ) {
        genres.forEachIndexed { index, genre ->
            val offsetX = index * (imageSize.value - overlap.value)
            val themeColor = remember(genre, isDark) {
                com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(
                    genre = genre,
                    isDark = isDark,
                    fallbackGenreId = genre.id
                )
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) }
                    .zIndex((numGenres - index).toFloat())
                    .size(imageSize)
                    .background(borderColor, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(borderWidth)
                        .clip(CircleShape)
                        .background(themeColor.container),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = themeColor.onContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
