package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.DayOfWeek
import android.widget.Toast

@Composable
fun AiRecommendationCard(
    playerViewModel: PlayerViewModel,
    recentlyPlayedSongs: List<Song>,
    modifier: Modifier = Modifier,
    isManualOnly: Boolean = true,
    onClickOpen: () -> Unit = {}
) {
    val isGenerating by playerViewModel.isGeneratingAiPlaylist.collectAsStateWithLifecycle()
    val aiError by playerViewModel.aiError.collectAsStateWithLifecycle()
    val aiSuccess by playerViewModel.aiSuccess.collectAsStateWithLifecycle()
    val generatedPlaylistSongs by playerViewModel.generatedPlaylistSongs.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    
    val generatedSongs = generatedPlaylistSongs.toImmutableList()
    val showGeneratedSongs = generatedSongs.isNotEmpty()
    
    LaunchedEffect(aiError) {
        aiError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }
    
    LaunchedEffect(aiSuccess) {
        if (aiSuccess) {
            Toast.makeText(context, context.getString(R.string.presentation_batch_e_ai_recommendation_success), Toast.LENGTH_SHORT).show()
        }
    }
    
    val recommendationContext by rememberRecommendationContext(recentlyPlayedSongs, context)
    
    val headerSongs = recentlyPlayedSongs.take(3)
    val cornerRadius = 28.dp
    
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusBR = cornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = cornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = cornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusTL = cornerRadius,
            smoothnessAsPercentBR = 60
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6200EE),
                                Color(0xFF03DAC6),
                                Color(0xFF2196F3)
                            )
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.gemini_ai),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Text(
                                text = recommendationContext.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            modifier = Modifier.padding(start = 1.dp),
                            text = recommendationContext.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy((-16).dp)
                    ) {
                        headerSongs.forEachIndexed { index, song ->
                            val size = when (index) {
                                0 -> 50.dp
                                1 -> 44.dp
                                else -> 48.dp
                            }
                            val paddingModifier = if (index == 0) Modifier.padding(top = 4.dp) 
                            else if (index == 1) Modifier.padding(bottom = 4.dp) 
                            else Modifier
                            
                            val shape = when (index) {
                                0 -> RoundedStarShape(sides = 6, rotation = 10f)
                                1 -> CircleShape
                                else -> AbsoluteSmoothCornerShape(
                                    cornerRadiusBL = 16.dp,
                                    cornerRadiusTR = 16.dp,
                                    smoothnessAsPercentBL = 60,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusTL = 16.dp,
                                    cornerRadiusBR = 16.dp,
                                    smoothnessAsPercentTL = 60,
                                    smoothnessAsPercentBR = 60
                                )
                            }
                            
                            Box(
                                modifier = paddingModifier
                                    .size(size)
                                    .clip(shape)
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), shape)
                            ) {
                                SmartImage(
                                    model = song.albumArtUriString,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    targetSize = SmartImageCompactListTargetSize
                                )
                            }
                        }
                    }
                }
            }
            
            if (recommendationContext.description.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "\"${recommendationContext.description}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (showGeneratedSongs && generatedSongs.isNotEmpty()) {
                AiSongList(
                    songs = generatedSongs,
                    playerViewModel = playerViewModel,
                    onMoreOptionsClick = { song ->
                        playerViewModel.selectSongForInfo(song)
                        showSongInfoSheet = true
                    },
                    queueName = recommendationContext.title,
                    stablePlayerState = stablePlayerState
                )
                
                ViewAllAiButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 10.dp,
                            end = 10.dp,
                            top = 6.dp,
                            bottom = 6.dp
                        ),
                    onClick = {
                        playerViewModel.generateAiPlaylist(
                            prompt = recommendationContext.prompt,
                            minLength = 10,
                            maxLength = 20,
                            saveAsPlaylist = true,
                            playlistName = recommendationContext.title,
                            force = true
                        )
                        onClickOpen()
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recommendationContext.tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF6200EE).copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF6200EE),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF6200EE)
                            )
                            Text(
                                text = context.getString(R.string.presentation_batch_e_ai_generating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    FilledTonalButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        onClick = {
                            playerViewModel.generateAiPlaylist(
                                prompt = recommendationContext.prompt,
                                minLength = 10,
                                maxLength = 20,
                                saveAsPlaylist = false,
                                force = true
                            )
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF6200EE),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_play_arrow_24),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = context.getString(R.string.presentation_batch_e_ai_generate),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
    
    if (showSongInfoSheet && selectedSongForInfo != null) {
        val song = selectedSongForInfo!!
        SongInfoBottomSheet(
            song = song,
            isFavorite = favoriteSongIds.contains(song.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = {
                showSongInfoSheet = false
                showPlaylistBottomSheet = false
            },
            onPlaySong = {
                playerViewModel.showAndPlaySong(
                    song = song,
                    contextSongs = generatedSongs,
                    queueName = recommendationContext.title,
                    isVoluntaryPlay = false
                )
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(song)
            },
            onAddNextToQueue = {
                playerViewModel.addSongNextToQueue(song)
            },
            onAddToPlayList = {
                showPlaylistBottomSheet = true
            },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                showSongInfoSheet = false
            },
            onNavigateToArtist = {
                showSongInfoSheet = false
            },
            onOpenNeteaseArtistHomepage = {
                playerViewModel.fetchNeteaseArtistId(song.neteaseId ?: 0L) { _ ->
                    showSongInfoSheet = false
                }
            },
            onNavigateToGenre = {
                showSongInfoSheet = false
            },
            onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                playerViewModel.editSongMetadata(
                    song,
                    newTitle,
                    newArtist,
                    newAlbum,
                    newAlbumArtist,
                    newComposer,
                    newGenre,
                    newLyrics,
                    newTrackNumber,
                    newDiscNumber,
                    replayGainTrackGainDb,
                    replayGainAlbumGainDb,
                    coverArtUpdate
                )
            },
            generateAiMetadata = { fields ->
                playerViewModel.generateAiMetadata(song, fields)
            },
            removeFromListTrigger = {}
        )
        
        if (showPlaylistBottomSheet) {
            val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = listOf(song),
                onDismiss = { showPlaylistBottomSheet = false },
                bottomBarHeight = 0.dp,
                playerViewModel = playerViewModel,
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AiSongList(
    songs: ImmutableList<Song>,
    playerViewModel: PlayerViewModel,
    onMoreOptionsClick: (Song) -> Unit,
    queueName: String,
    stablePlayerState: com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
) {
    val visibleSongs = songs.take(4).toImmutableList()
    val itemContainerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        visibleSongs.forEach { song ->
            EnhancedSongListItem(
                song = song,
                isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                isPlaying = stablePlayerState.isPlaying && stablePlayerState.currentSong?.id == song.id,
                containerColorOverride = itemContainerColor,
                onMoreOptionsClick = onMoreOptionsClick,
                customShape = RoundedCornerShape(10.dp),
                showAlbumArt = false,
                onClick = {
                    playerViewModel.showAndPlaySong(
                        song = song,
                        contextSongs = songs,
                        queueName = queueName,
                        isVoluntaryPlay = false
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ViewAllAiButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.Transparent
        ),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 10.dp,
            cornerRadiusTR = 10.dp,
            smoothnessAsPercentTL = 70,
            smoothnessAsPercentTR = 70,
            cornerRadiusBL = 60.dp,
            cornerRadiusBR = 60.dp,
            smoothnessAsPercentBL = 70,
            smoothnessAsPercentBR = 70
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_e_ai_view_all_mix),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6200EE)
            )
            Icon(
                painter = painterResource(R.drawable.rounded_arrow_forward_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF6200EE)
            )
        }
    }
}

@Composable
private fun rememberRecommendationContext(
    recentlyPlayedSongs: List<Song>,
    context: android.content.Context
): androidx.compose.runtime.State<RecommendationContext> {
    return remember {
        derivedStateOf {
            buildRecommendationContext(recentlyPlayedSongs, context)
        }
    }
}

private data class RecommendationContext(
    val title: String,
    val subtitle: String,
    val description: String,
    val prompt: String,
    val tags: List<String>
)

private fun buildRecommendationContext(
    recentlyPlayedSongs: List<Song>,
    context: android.content.Context
): RecommendationContext {
    val now = LocalDateTime.now()
    val timeOfDay = getTimeOfDay(now.toLocalTime(), context)
    val dayOfWeek = now.dayOfWeek
    val holiday = getHoliday(now.toLocalDate(), context)
    
    val recentArtists = recentlyPlayedSongs.take(10).mapNotNull { it.displayArtist.takeIf { it.isNotEmpty() } }.distinct()
    val recentGenres = recentlyPlayedSongs.take(10).mapNotNull { it.genre }.distinct()
    
    val tags = mutableListOf<String>()
    tags.add(timeOfDay.tag)
    if (holiday != null) tags.add(holiday.tag)
    
    val description = generateRecommendationDescription(timeOfDay, holiday, recentArtists, recentGenres, context)
    
    val contextParts = mutableListOf<String>()
    contextParts.add("Time of day: ${timeOfDay.description}")
    contextParts.add("Day: ${dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}")
    if (holiday != null) contextParts.add("Holiday: ${holiday.name}")
    if (recentArtists.isNotEmpty()) {
        contextParts.add("Recent artists: ${recentArtists.joinToString(", ")}")
        tags.addAll(recentArtists.take(2))
    }
    if (recentGenres.isNotEmpty()) {
        contextParts.add("Recent genres: ${recentGenres.joinToString(", ")}")
        tags.addAll(recentGenres.take(2))
    }
    
    val prompt = """
        Create a personalized playlist recommendation based on the following context:
        
        ${contextParts.joinToString("\n")}
        
        Please recommend songs that match the user's recent listening habits and the current context.
        Return only song names with artists in the format:
        - Song Title - Artist Name
        """.trimIndent()
    
    return RecommendationContext(
        title = timeOfDay.title,
        subtitle = timeOfDay.subtitle,
        description = description,
        prompt = prompt,
        tags = tags.take(5)
    )
}

private fun generateRecommendationDescription(
    timeOfDay: TimeOfDayInfo,
    holiday: HolidayInfo?,
    recentArtists: List<String>,
    recentGenres: List<String>,
    context: android.content.Context
): String {
    val descriptions = listOf(
        context.getString(R.string.presentation_batch_e_ai_desc_1),
        context.getString(R.string.presentation_batch_e_ai_desc_2),
        context.getString(R.string.presentation_batch_e_ai_desc_3),
        context.getString(R.string.presentation_batch_e_ai_desc_4),
        context.getString(R.string.presentation_batch_e_ai_desc_5)
    )
    
    return descriptions.random()
}

private fun getTimeOfDay(time: LocalTime, context: android.content.Context): TimeOfDayInfo {
    return when {
        time.isAfter(LocalTime.of(5, 0)) && time.isBefore(LocalTime.of(9, 0)) -> TimeOfDayInfo(
            title = context.getString(R.string.presentation_batch_e_ai_morning_title),
            subtitle = context.getString(R.string.presentation_batch_e_ai_morning_subtitle),
            description = "morning",
            tag = context.getString(R.string.presentation_batch_e_ai_tag_morning)
        )
        time.isAfter(LocalTime.of(9, 0)) && time.isBefore(LocalTime.of(12, 0)) -> TimeOfDayInfo(
            title = context.getString(R.string.presentation_batch_e_ai_midday_title),
            subtitle = context.getString(R.string.presentation_batch_e_ai_midday_subtitle),
            description = "midday",
            tag = context.getString(R.string.presentation_batch_e_ai_tag_work)
        )
        time.isAfter(LocalTime.of(12, 0)) && time.isBefore(LocalTime.of(14, 0)) -> TimeOfDayInfo(
            title = context.getString(R.string.presentation_batch_e_ai_lunch_title),
            subtitle = context.getString(R.string.presentation_batch_e_ai_lunch_subtitle),
            description = "lunchtime",
            tag = context.getString(R.string.presentation_batch_e_ai_tag_relax)
        )
        time.isAfter(LocalTime.of(14, 0)) && time.isBefore(LocalTime.of(18, 0)) -> TimeOfDayInfo(
            title = context.getString(R.string.presentation_batch_e_ai_afternoon_title),
            subtitle = context.getString(R.string.presentation_batch_e_ai_afternoon_subtitle),
            description = "afternoon",
            tag = context.getString(R.string.presentation_batch_e_ai_tag_focus)
        )
        time.isAfter(LocalTime.of(18, 0)) && time.isBefore(LocalTime.of(21, 0)) -> TimeOfDayInfo(
            title = context.getString(R.string.presentation_batch_e_ai_evening_title),
            subtitle = context.getString(R.string.presentation_batch_e_ai_evening_subtitle),
            description = "evening",
            tag = context.getString(R.string.presentation_batch_e_ai_tag_chill)
        )
        time.isAfter(LocalTime.of(21, 0)) || time.isBefore(LocalTime.of(2, 0)) -> TimeOfDayInfo(
            title = context.getString(R.string.presentation_batch_e_ai_night_title),
            subtitle = context.getString(R.string.presentation_batch_e_ai_night_subtitle),
            description = "night",
            tag = context.getString(R.string.presentation_batch_e_ai_tag_night)
        )
        else -> TimeOfDayInfo(
            title = context.getString(R.string.presentation_batch_e_ai_late_night_title),
            subtitle = context.getString(R.string.presentation_batch_e_ai_late_night_subtitle),
            description = "late night",
            tag = context.getString(R.string.presentation_batch_e_ai_tag_sleep)
        )
    }
}

private data class TimeOfDayInfo(
    val title: String,
    val subtitle: String,
    val description: String,
    val tag: String
)

private data class HolidayInfo(
    val name: String,
    val tag: String
)

private fun getHoliday(date: LocalDate, context: android.content.Context): HolidayInfo? {
    val month = date.month
    val dayOfMonth = date.dayOfMonth
    
    return when {
        month == Month.JANUARY && dayOfMonth == 1 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_new_year),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_new_year_tag)
        )
        month == Month.FEBRUARY && dayOfMonth == 14 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_valentine),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_valentine_tag)
        )
        month == Month.MARCH && dayOfMonth == 8 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_womens),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_womens_tag)
        )
        month == Month.MARCH && dayOfMonth == 17 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_st_patrick),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_st_patrick_tag)
        )
        month == Month.APRIL && dayOfMonth == 1 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_april_fools),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_april_fools_tag)
        )
        month == Month.MAY && dayOfMonth == 1 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_labor),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_labor_tag)
        )
        month == Month.MAY && dayOfMonth == 14 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_mothers),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_mothers_tag)
        )
        month == Month.JUNE && dayOfMonth == 21 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_fathers),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_fathers_tag)
        )
        month == Month.JULY && dayOfMonth == 4 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_independence),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_independence_tag)
        )
        month == Month.OCTOBER && dayOfMonth == 31 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_halloween),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_halloween_tag)
        )
        month == Month.NOVEMBER && dayOfMonth == 11 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_veterans),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_veterans_tag)
        )
        month == Month.NOVEMBER && dayOfMonth == 27 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_thanksgiving),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_thanksgiving_tag)
        )
        month == Month.DECEMBER && dayOfMonth == 25 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_christmas),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_christmas_tag)
        )
        month == Month.DECEMBER && dayOfMonth == 31 -> HolidayInfo(
            name = context.getString(R.string.presentation_batch_e_ai_holiday_new_year_eve),
            tag = context.getString(R.string.presentation_batch_e_ai_holiday_new_year_eve_tag)
        )
        else -> null
    }
}