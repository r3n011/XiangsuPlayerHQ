package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PlaylistEvaluation(
    val rating: Int,
    val cohesion: Int,
    val diversity: Int,
    val energyFlow: Int,
    val comment: String,
    val suggestions: List<String>
)

@Singleton
class AiPlaylistEvaluator @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val preferencesRepo: AiPreferencesRepository,
    private val json: Json
) {

    suspend fun evaluatePlaylist(
        playlistName: String,
        songs: List<Song>,
        userPrompt: String = "",
        force: Boolean = false
    ): Result<PlaylistEvaluation> {
        return try {
            if (!force && !preferencesRepo.isAutoPlaylistEvaluationEnabled.first()) {
                return Result.failure(Exception("AI auto-trigger is disabled"))
            }
            val songInfo = buildSongInfo(songs)
            
            val prompt = """
                <playlist>
                <name>$playlistName</name>
                <songs>
                $songInfo
                </songs>
                ${if (userPrompt.isNotBlank()) "<user_prompt>$userPrompt</user_prompt>" else ""}
                </playlist>
            """.trimIndent()

            val responseText = aiOrchestrator.generateContent(
                prompt = prompt,
                type = AiSystemPromptType.PLAYLIST_EVALUATION,
                temperature = 0.4f
            )

            val evaluation = parseEvaluation(responseText)
            Result.success(evaluation)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildSongInfo(songs: List<Song>): String {
        return buildString {
            songs.forEachIndexed { index, song ->
                val title = song.title.replace("\"", "'").take(50)
                val artist = song.displayArtist.replace("\"", "'").take(30)
                val album = song.album?.replace("\"", "'")?.take(30) ?: ""
                val genre = song.genre?.replace("\"", "'")?.take(20) ?: ""
                val duration = formatDuration(song.duration)
                
                append("""{"index":${index+1},"title":"$title","artist":"$artist","album":"$album","genre":"$genre","duration":"$duration"}""")
                if (index < songs.size - 1) append("\n")
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).toInt()
        val minutes = seconds / 60
        val secs = seconds % 60
        return "$minutes:$secs"
    }

    private fun parseEvaluation(rawResponse: String): PlaylistEvaluation {
        val sanitized = rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return try {
            json.decodeFromString<PlaylistEvaluation>(sanitized)
        } catch (e: Exception) {
            PlaylistEvaluation(
                rating = 5,
                cohesion = 5,
                diversity = 5,
                energyFlow = 5,
                comment = rawResponse.takeIf { it.isNotBlank() } ?: "Unable to parse structured evaluation.",
                suggestions = emptyList()
            )
        }
    }
}
