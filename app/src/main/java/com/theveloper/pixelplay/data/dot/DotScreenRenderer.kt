package com.theveloper.pixelplay.data.dot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.utils.formatListeningDurationCompact
import kotlin.math.roundToInt

enum class DotDisplayMode {
    NOW_PLAYING,
    TODAY_STATS,
    MONTH_STATS,
    ALL_TIME_STATS,
    TOP_SONGS,
    TOP_ARTISTS,
    TIME_DISTRIBUTION
}

object DotScreenRenderer {

    private const val WIDTH = 296
    private const val HEIGHT = 152

    private val BLACK = Color.BLACK
    private val WHITE = Color.WHITE
    private val GRAY = Color.GRAY
    private val LIGHT_GRAY = Color.LTGRAY

    fun createNowPlayingBitmap(
        songTitle: String,
        artistName: String,
        albumArt: Bitmap?,
        progressPercent: Float,
        isPlaying: Boolean
    ): Bitmap {
        val bitmap = createBlankBitmap()
        val canvas = Canvas(bitmap)

        val padding = 6
        val albumArtSize = 120

        // Album art on left
        if (albumArt != null) {
            val scaledAlbumArt = Bitmap.createScaledBitmap(albumArt, albumArtSize, albumArtSize, true)
            canvas.drawBitmap(scaledAlbumArt, padding.toFloat(), padding.toFloat(), null)
            if (!scaledAlbumArt.isRecycled) scaledAlbumArt.recycle()
        } else {
            val placeholderPaint = Paint().apply {
                color = LIGHT_GRAY
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                padding.toFloat(), padding.toFloat(),
                (padding + albumArtSize).toFloat(), (padding + albumArtSize).toFloat(),
                placeholderPaint
            )
            val textPaint = Paint().apply {
                color = GRAY
                textSize = 40f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("♫", (padding + albumArtSize / 2).toFloat(), (padding + albumArtSize / 2 + 14).toFloat(), textPaint)
        }

        val textStartX = padding + albumArtSize + padding + 4
        val availableWidth = WIDTH - textStartX - padding

        // Song title - large and bold
        val titlePaint = Paint().apply {
            color = BLACK
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val titleLines = breakText(songTitle, titlePaint, availableWidth)
        var y = padding + 28
        for (i in 0 until minOf(2, titleLines.size)) {
            canvas.drawText(titleLines[i], textStartX.toFloat(), y.toFloat(), titlePaint)
            y += 30
        }

        // Artist name
        val artistPaint = Paint().apply {
            color = BLACK
            textSize = 20f
            isAntiAlias = true
        }
        val artistLines = breakText(artistName, artistPaint, availableWidth)
        if (artistLines.isNotEmpty()) {
            canvas.drawText(artistLines[0], textStartX.toFloat(), y.toFloat(), artistPaint)
        }
        y += 24

        // Status indicator
        val statusPaint = Paint().apply {
            color = if (isPlaying) BLACK else GRAY
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val statusText = if (isPlaying) "▶ 播放中" else "⏸ 已暂停"
        canvas.drawText(statusText, textStartX.toFloat(), y.toFloat(), statusPaint)

        // Progress bar at bottom
        val progressY = HEIGHT - padding - 24
        val progressHeight = 14
        val progressWidth = WIDTH - padding * 2

        // Track background
        val trackPaint = Paint().apply {
            color = LIGHT_GRAY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(
            padding.toFloat(), progressY.toFloat(),
            (padding + progressWidth).toFloat(), (progressY + progressHeight).toFloat(),
            trackPaint
        )

        // Progress fill
        val progressPaint = Paint().apply {
            color = BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val progressW = (progressWidth * progressPercent.coerceIn(0f, 1f)).toInt()
        canvas.drawRect(
            padding.toFloat(), progressY.toFloat(),
            (padding + progressW).toFloat(), (progressY + progressHeight).toFloat(),
            progressPaint
        )

        // Time labels below progress bar
        val timePaint = Paint().apply {
            color = BLACK
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val timeText = formatTime(progressPercent)
        canvas.drawText(timeText.first, padding.toFloat(), (progressY + progressHeight + 14).toFloat(), timePaint)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeText.second, (WIDTH - padding).toFloat(), (progressY + progressHeight + 14).toFloat(), timePaint)

        return bitmap
    }

    fun createStatsOverviewBitmap(
        summary: PlaybackStatsRepository.PlaybackStatsSummary
    ): Bitmap {
        val bitmap = createBlankBitmap()
        val canvas = Canvas(bitmap)

        val padding = 6
        var y = padding

        // Title row: "听歌统计" on left, range on right
        val titlePaint = Paint().apply {
            color = BLACK
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = BLACK
            textSize = 14f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("听歌统计", padding.toFloat(), (y + 20).toFloat(), titlePaint)
        canvas.drawText(getRangeDisplayName(summary.range), (WIDTH - padding).toFloat(), (y + 20).toFloat(), subtitlePaint)
        y += 28

        // Big number: total duration - very prominent
        val bigNumberPaint = Paint().apply {
            color = BLACK
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val durationText = formatListeningDurationCompact(summary.totalDurationMs)
        canvas.drawText(durationText, padding.toFloat(), (y + 42).toFloat(), bigNumberPaint)
        y += 50

        // Stats row: play count + average daily
        val statsRowY = y
        val statValuePaint = Paint().apply {
            color = BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val statLabelPaint = Paint().apply {
            color = BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val col1X = padding
        val col2X = WIDTH / 2 + 8

        canvas.drawText(summary.totalPlayCount.toString(), col1X.toFloat(), (statsRowY + 22).toFloat(), statValuePaint)
        canvas.drawText("播放次数", col1X.toFloat(), (statsRowY + 36).toFloat(), statLabelPaint)

        val avgDailyText = formatListeningDurationCompact(summary.averageDailyDurationMs)
        canvas.drawText(avgDailyText, col2X.toFloat(), (statsRowY + 22).toFloat(), statValuePaint)
        canvas.drawText("平均每日", col2X.toFloat(), (statsRowY + 36).toFloat(), statLabelPaint)

        y = statsRowY + 42

        // Top track section - with separator
        val topTrack = summary.topSongs.firstOrNull()
        if (topTrack != null) {
            // Draw separator line
            val separatorPaint = Paint().apply {
                color = LIGHT_GRAY
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRect(padding.toFloat(), y.toFloat(), (WIDTH - padding).toFloat(), (y + 2).toFloat(), separatorPaint)
            y += 8

            val topTrackLabelPaint = Paint().apply {
                color = BLACK
                textSize = 12f
                isAntiAlias = true
            }
            val topTrackTitlePaint = Paint().apply {
                color = BLACK
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val topTrackDescPaint = Paint().apply {
                color = BLACK
                textSize = 13f
                isAntiAlias = true
            }
            val textMaxWidth = WIDTH - padding * 2

            canvas.drawText("最常听歌曲", padding.toFloat(), (y + 12).toFloat(), topTrackLabelPaint)
            y += 18

            val titleLines = breakText(topTrack.title, topTrackTitlePaint, textMaxWidth)
            if (titleLines.isNotEmpty()) {
                canvas.drawText(titleLines[0], padding.toFloat(), (y + 16).toFloat(), topTrackTitlePaint)
            }
            y += 18

            val descText = "${topTrack.artist} · ${topTrack.playCount}次"
            val descLines = breakText(descText, topTrackDescPaint, textMaxWidth)
            if (descLines.isNotEmpty()) {
                canvas.drawText(descLines[0], padding.toFloat(), (y + 13).toFloat(), topTrackDescPaint)
            }
            y += 16
        }

        // Timeline fills remaining space at bottom
        val timelineTop = y + 4
        val timelineHeight = (HEIGHT - timelineTop - padding).coerceAtLeast(20)
        drawMiniTimeline(canvas, summary.timeline, summary.range, padding, timelineTop, WIDTH - padding * 2, timelineHeight)

        return bitmap
    }

    fun createTopSongsBitmap(
        songs: List<PlaybackStatsRepository.SongPlaybackSummary>,
        title: String
    ): Bitmap {
        val bitmap = createBlankBitmap()
        val canvas = Canvas(bitmap)

        val padding = 6
        var y = padding

        // Title
        val titlePaint = Paint().apply {
            color = BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText(title, padding.toFloat(), (y + 22).toFloat(), titlePaint)
        y += 30

        val rankPaint = Paint().apply {
            color = BLACK
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val songTitlePaint = Paint().apply {
            color = BLACK
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val songArtistPaint = Paint().apply {
            color = BLACK
            textSize = 14f
            isAntiAlias = true
        }
        val countPaint = Paint().apply {
            color = BLACK
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val rankWidth = 28
        val countWidth = 60
        val textStartX = padding + rankWidth + 4
        val textMaxWidth = WIDTH - padding * 2 - rankWidth - countWidth - 8

        // Show max 4 items for better readability
        val maxItems = minOf(songs.size, 4)
        val itemHeight = (HEIGHT - y - padding) / maxItems

        for (i in 0 until maxItems) {
            val song = songs[i]
            val itemY = y + i * itemHeight + itemHeight / 2

            // Draw separator line between items (not before first)
            if (i > 0) {
                val separatorPaint = Paint().apply {
                    color = LIGHT_GRAY
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRect(
                    padding.toFloat(), (y + i * itemHeight).toFloat(),
                    (WIDTH - padding).toFloat(), (y + i * itemHeight + 2).toFloat(),
                    separatorPaint
                )
            }

            val rank = (i + 1).toString()
            canvas.drawText(rank, (padding + rankWidth / 2).toFloat(), (itemY - 2).toFloat(), rankPaint)

            val titleLines = breakText(song.title, songTitlePaint, textMaxWidth)
            if (titleLines.isNotEmpty()) {
                canvas.drawText(titleLines[0], textStartX.toFloat(), (itemY - 6).toFloat(), songTitlePaint)
            }

            val artistLines = breakText(song.artist, songArtistPaint, textMaxWidth)
            if (artistLines.isNotEmpty()) {
                canvas.drawText(artistLines[0], textStartX.toFloat(), (itemY + 12).toFloat(), songArtistPaint)
            }

            val playCountText = "${song.playCount}次"
            canvas.drawText(playCountText, (WIDTH - padding).toFloat(), (itemY).toFloat(), countPaint)
        }

        return bitmap
    }

    fun createTopArtistsBitmap(
        artists: List<PlaybackStatsRepository.ArtistPlaybackSummary>,
        title: String
    ): Bitmap {
        val bitmap = createBlankBitmap()
        val canvas = Canvas(bitmap)

        val padding = 6
        var y = padding

        // Title
        val titlePaint = Paint().apply {
            color = BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText(title, padding.toFloat(), (y + 22).toFloat(), titlePaint)
        y += 30

        val rankPaint = Paint().apply {
            color = BLACK
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val artistNamePaint = Paint().apply {
            color = BLACK
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val detailPaint = Paint().apply {
            color = BLACK
            textSize = 13f
            isAntiAlias = true
        }
        val durationPaint = Paint().apply {
            color = BLACK
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val rankWidth = 28
        val durWidth = 60
        val textStartX = padding + rankWidth + 4
        val textMaxWidth = WIDTH - padding * 2 - rankWidth - durWidth - 8

        // Show max 4 items for better readability
        val maxItems = minOf(artists.size, 4)
        val itemHeight = (HEIGHT - y - padding) / maxItems

        for (i in 0 until maxItems) {
            val artist = artists[i]
            val itemY = y + i * itemHeight + itemHeight / 2

            // Draw separator line between items (not before first)
            if (i > 0) {
                val separatorPaint = Paint().apply {
                    color = LIGHT_GRAY
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRect(
                    padding.toFloat(), (y + i * itemHeight).toFloat(),
                    (WIDTH - padding).toFloat(), (y + i * itemHeight + 2).toFloat(),
                    separatorPaint
                )
            }

            val rank = (i + 1).toString()
            canvas.drawText(rank, (padding + rankWidth / 2).toFloat(), (itemY - 2).toFloat(), rankPaint)

            val nameLines = breakText(artist.artist, artistNamePaint, textMaxWidth)
            if (nameLines.isNotEmpty()) {
                canvas.drawText(nameLines[0], textStartX.toFloat(), (itemY - 4).toFloat(), artistNamePaint)
            }

            val detailText = "${artist.uniqueSongs}首歌 · ${artist.playCount}次"
            canvas.drawText(detailText, textStartX.toFloat(), (itemY + 14).toFloat(), detailPaint)

            val durText = formatListeningDurationCompact(artist.totalDurationMs)
            canvas.drawText(durText, (WIDTH - padding).toFloat(), (itemY).toFloat(), durationPaint)
        }

        return bitmap
    }

    fun createTimeDistributionBitmap(
        distribution: PlaybackStatsRepository.DayListeningDistribution,
        title: String
    ): Bitmap {
        val bitmap = createBlankBitmap()
        val canvas = Canvas(bitmap)

        val padding = 6
        var y = padding

        // Title
        val titlePaint = Paint().apply {
            color = BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText(title, padding.toFloat(), (y + 22).toFloat(), titlePaint)
        y += 30

        val hourBuckets = aggregateByHour(distribution)
        val maxDuration = hourBuckets.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1L

        val chartTop = y
        val chartHeight = 80
        val chartWidth = WIDTH - padding * 2

        val barPaint = Paint().apply {
            color = BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val barCount = 24
        val gapWidth = 1
        val barWidth = (chartWidth - gapWidth * (barCount - 1)) / barCount

        for (i in 0 until barCount) {
            val duration = hourBuckets[i].second
            val heightFraction = duration.toFloat() / maxDuration.toFloat()
            val barHeight = (chartHeight * heightFraction).coerceAtLeast(2f).toInt()

            val left = padding + i * (barWidth + gapWidth)
            val top = chartTop + chartHeight - barHeight
            val right = left + barWidth
            val bottom = chartTop + chartHeight

            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), barPaint)
        }

        // Hour labels
        val labelPaint = Paint().apply {
            color = BLACK
            textSize = 12f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val labelY = chartTop + chartHeight + 14
        for (i in listOf(0, 6, 12, 18, 23)) {
            val x = padding + i * (barWidth + gapWidth) + barWidth / 2
            canvas.drawText("${i}h", x.toFloat(), labelY.toFloat(), labelPaint)
        }

        // Peak hour info at bottom
        val bottomY = labelY + 16
        val peakLabelPaint = Paint().apply {
            color = BLACK
            textSize = 14f
            isAntiAlias = true
        }
        val peakValuePaint = Paint().apply {
            color = BLACK
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val peakHour = hourBuckets.maxByOrNull { it.second }
        if (peakHour != null && peakHour.second > 0) {
            canvas.drawText("听歌高峰", padding.toFloat(), (bottomY + 14).toFloat(), peakLabelPaint)
            val peakText = "${peakHour.first}:00 · ${formatListeningDurationCompact(peakHour.second)}"
            canvas.drawText(peakText, padding.toFloat(), (bottomY + 32).toFloat(), peakValuePaint)
        }

        return bitmap
    }

    private fun createBlankBitmap(): Bitmap {
        return Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply {
            eraseColor(WHITE)
        }
    }

    private fun breakText(text: String, paint: Paint, maxWidth: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (char in text) {
            currentLine.append(char)
            if (paint.measureText(currentLine.toString()) > maxWidth) {
                if (currentLine.length > 1) {
                    currentLine.deleteCharAt(currentLine.length - 1)
                    lines.add(currentLine.toString().trim())
                    currentLine = StringBuilder(char.toString())
                } else {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString().trim())
        }
        return lines.takeIf { it.isNotEmpty() } ?: listOf(text)
    }

    private fun formatTime(progressPercent: Float): Pair<String, String> {
        val total = 240
        val current = (total * progressPercent).toInt()
        return formatMinutes(current) to formatMinutes(total)
    }

    private fun formatMinutes(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun drawMiniTimeline(
        canvas: Canvas,
        timeline: List<PlaybackStatsRepository.TimelineEntry>,
        range: StatsTimeRange,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ) {
        if (timeline.isEmpty()) return

        val entries = timeline.takeLast(minOf(7, timeline.size))
        if (entries.isEmpty()) return

        val maxDuration = entries.maxOfOrNull { it.totalDurationMs }?.takeIf { it > 0 } ?: 1L

        val barPaint = Paint().apply {
            color = BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = BLACK
            textSize = 12f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val gap = 6
        val barWidth = (width - gap * (entries.size - 1)) / entries.size
        val labelHeight = 18
        val barAreaHeight = height - labelHeight - 4

        for (i in entries.indices) {
            val entry = entries[i]
            val heightFraction = entry.totalDurationMs.toFloat() / maxDuration.toFloat()
            val barHeight = (barAreaHeight * heightFraction).coerceAtLeast(4f).toInt()

            val x = left + i * (barWidth + gap)
            val barTop = top + barAreaHeight - barHeight
            val barBottom = top + barAreaHeight

            canvas.drawRect(x.toFloat(), barTop.toFloat(), (x + barWidth).toFloat(), barBottom.toFloat(), barPaint)

            val labelY = top + height - 2
            val label = entry.label.let {
                if (it.length > 3) it.take(3) else it
            }
            canvas.drawText(label, (x + barWidth / 2).toFloat(), labelY.toFloat(), labelPaint)
        }
    }

    private fun aggregateByHour(distribution: PlaybackStatsRepository.DayListeningDistribution): List<Pair<Int, Long>> {
        val hourTotals = LongArray(24)
        for (bucket in distribution.buckets) {
            val startHour = bucket.startMinute / 60
            if (startHour in 0..23) {
                hourTotals[startHour] += bucket.totalDurationMs
            }
        }
        return (0 until 24).map { it to hourTotals[it] }
    }

    private fun getRangeDisplayName(range: StatsTimeRange): String {
        return when (range) {
            StatsTimeRange.DAY -> "\u4ECA\u65E5"
            StatsTimeRange.WEEK -> "\u672C\u5468"
            StatsTimeRange.MONTH -> "\u672C\u6708"
            StatsTimeRange.YEAR -> "\u672C\u5E74"
            StatsTimeRange.ALL -> "\u7D2F\u8BA1"
        }
    }
}
