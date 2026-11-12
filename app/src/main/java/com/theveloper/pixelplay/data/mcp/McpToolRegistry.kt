package com.theveloper.pixelplay.data.mcp

import androidx.media3.common.Player
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.ai.UserProfileDigestGenerator
import com.theveloper.pixelplay.data.cloudsearch.BuiltInSourceSearchApi
import com.theveloper.pixelplay.data.lx.LxSearchResult
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlaybackStateHolder
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private data class LivePlayerSnapshot(
    val isPlaying: Boolean,
    val positionMs: Long,
    val mediaId: String?,
    val queueIndex: Int,
    val queueSize: Int,
)

interface McpTool {
    val name: String
    val description: String
    val scope: String
    val inputSchema: JsonObject
    suspend fun invoke(args: JsonObject): McpToolResult
}

/**
 * Tool catalog. Every handler maps to an existing Kotlin API — this class must not become a
 * second source of business logic. Read-only tools never expose local file paths or stream URLs.
 */
@Singleton
class McpToolRegistry @Inject constructor(
    private val state: PlaybackStateHolder,
    private val engine: DualPlayerEngine,
    private val music: MusicRepository,
    private val playlists: PlaylistPreferencesRepository,
    private val dailyMix: DailyMixManager,
    private val digest: UserProfileDigestGenerator,
    private val onlineSources: BuiltInSourceSearchApi,
) {
    private val tools: List<McpTool> = buildList {
        add(playerGetState())
        add(simple("player_play", "开始播放", McpScopes.CONTROL_PLAYBACK) {
            withContext(Dispatchers.Main.immediate) { engine.masterPlayer.play() }
            McpToolResult.ok("已开始播放。")
        })
        add(simple("player_pause", "暂停播放", McpScopes.CONTROL_PLAYBACK) {
            withContext(Dispatchers.Main.immediate) { engine.masterPlayer.pause() }
            McpToolResult.ok("已暂停。")
        })
        add(simple("player_toggle", "播放/暂停切换", McpScopes.CONTROL_PLAYBACK) {
            withContext(Dispatchers.Main.immediate) { state.playPause() }
            McpToolResult.ok("已切换播放状态。")
        })
        add(simple("player_next", "下一首", McpScopes.CONTROL_PLAYBACK) {
            withContext(Dispatchers.Main.immediate) { state.nextSong() }
            McpToolResult.ok("已切到下一首。")
        })
        add(simple("player_previous", "上一首", McpScopes.CONTROL_PLAYBACK) {
            withContext(Dispatchers.Main.immediate) { state.previousSong() }
            McpToolResult.ok("已切到上一首。")
        })
        add(playerSeek())
        add(playerSetVolume())
        add(playerSetRepeat())
        add(playerSetShuffle())
        add(playerSetFavorite())
        add(librarySearch())
        add(libraryGetSong())
        add(libraryListPlaylists())
        add(libraryGetPlaylist())
        add(libraryCreatePlaylist())
        add(libraryAddToPlaylist())
        add(aiGetCandidates())
        add(aiGetProfile())
        add(onlineSearch())
        add(lyricsGet())
        add(queuePlay())
        add(appOpenSettings())
        add(appNavigate())
        add(systemCapabilities())
    }

    fun available(scopes: Set<String>): List<McpTool> = tools.filter { it.scope in scopes }

    fun allDefinitions(): JsonObject = buildJsonObject {
        putJsonArray("tools") {
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", tool.inputSchema)
                })
            }
        }
    }

    suspend fun call(name: String, scopes: Set<String>, args: JsonObject): McpToolResult {
        val tool = tools.firstOrNull { it.name == name }
            ?: return McpToolResult.fail("未知工具：$name")
        if (tool.scope !in scopes) {
            return McpToolResult.fail("当前客户端没有 ${tool.scope} 权限")
        }
        return runCatching { tool.invoke(args) }
            .onFailure { Timber.w(it, "MCP tool failed: %s", name) }
            .getOrElse { McpToolResult.fail(it.message ?: "工具执行失败") }
    }

    // ---- playback ----

    private fun playerGetState(): McpTool = object : McpTool {
        override val name = "player_get_state"
        override val description = "读取当前歌曲、播放进度、时长、播放状态、循环模式、随机模式、音量与队列位置。"
        override val scope = McpScopes.READ_PLAYBACK
        override val inputSchema = objectSchema(emptyMap(), emptyList())

        override suspend fun invoke(args: JsonObject): McpToolResult {
            val snapshot = state.stablePlayerState.value
            // PlaybackStateHolder can be empty after the UI process loses its ViewModel while
            // the media session keeps playing; fall back to the live player like Web Remote does.
            val live = withContext(Dispatchers.Main.immediate) {
                runCatching {
                    val player = engine.masterPlayer
                    LivePlayerSnapshot(
                        isPlaying = player.isPlaying,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        mediaId = player.currentMediaItem?.mediaId,
                        queueIndex = player.currentMediaItemIndex,
                        queueSize = player.mediaItemCount,
                    )
                }.getOrNull()
            }
            val position = live?.positionMs ?: state.currentPosition.value
            var song = snapshot.currentSong
            if (song == null && !live?.mediaId.isNullOrBlank()) {
                song = music.getSong(live!!.mediaId!!).first()
            }
            val structured = buildJsonObject {
                song?.let { put("song", it.toMcpJson()) }
                put("positionMs", position)
                put("isPlaying", live?.isPlaying ?: snapshot.isPlaying)
                put("repeatMode", when (snapshot.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "one"
                    Player.REPEAT_MODE_ALL -> "all"
                    else -> "off"
                })
                put("shuffle", snapshot.isShuffleEnabled)
                put("volume", engine.currentVolume.toDouble())
                putJsonObject("queue") {
                    put("index", live?.queueIndex ?: snapshot.currentMediaItemIndex)
                    put("size", live?.queueSize ?: 0)
                }
            }
            val text = if (song == null) {
                "当前没有正在播放的歌曲。"
            } else {
                "当前播放：《${song.title}》- ${song.displayArtist}，${formatMs(position)} / ${formatMs(song.duration)}"
            }
            return McpToolResult.ok(text, structured)
        }
    }

    private fun playerSeek(): McpTool = tool(
        "player_seek",
        "跳转到指定播放位置（毫秒）",
        McpScopes.CONTROL_PLAYBACK,
        objectSchema(mapOf("positionMs" to intProp("目标位置，毫秒", minimum = 0)), listOf("positionMs")),
    ) { args ->
        val position = args.int("positionMs") ?: return@tool McpToolResult.fail("缺少 positionMs")
        withContext(Dispatchers.Main.immediate) { state.seekTo(position.toLong().coerceAtLeast(0L)) }
        McpToolResult.ok("已跳转到 ${formatMs(position.toLong())}。")
    }

    private fun playerSetVolume(): McpTool = tool(
        "player_set_volume",
        "设置播放器音量（0 到 1）",
        McpScopes.CONTROL_PLAYBACK,
        objectSchema(
            mapOf("volume" to numProp("音量，0 到 1", minimum = 0.0, maximum = 1.0)),
            listOf("volume"),
        ),
    ) { args ->
        val volume = args["volume"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull }
            ?: return@tool McpToolResult.fail("缺少 volume")
        engine.setVolume(volume.toFloat())
        McpToolResult.ok("音量已设为 ${(volume.coerceIn(0.0, 1.0) * 100).toInt()}%。")
    }

    private fun playerSetRepeat(): McpTool = tool(
        "player_set_repeat",
        "设置循环模式：off / one / all",
        McpScopes.CONTROL_PLAYBACK,
        objectSchema(
            mapOf("mode" to enumProp("循环模式", listOf("off", "one", "all"))),
            listOf("mode"),
        ),
    ) { args ->
        val mode = args.string("mode") ?: return@tool McpToolResult.fail("缺少 mode")
        val value = when (mode) {
            "off" -> Player.REPEAT_MODE_OFF
            "one" -> Player.REPEAT_MODE_ONE
            "all" -> Player.REPEAT_MODE_ALL
            else -> return@tool McpToolResult.fail("mode 必须是 off/one/all")
        }
        withContext(Dispatchers.Main.immediate) { state.setRepeatMode(value) }
        McpToolResult.ok("循环模式已设为 $mode。")
    }

    private fun playerSetShuffle(): McpTool = tool(
        "player_set_shuffle",
        "开启或关闭随机播放",
        McpScopes.CONTROL_PLAYBACK,
        objectSchema(mapOf("enabled" to boolProp("是否开启随机")), listOf("enabled")),
    ) { args ->
        val enabled = args.bool("enabled") ?: return@tool McpToolResult.fail("缺少 enabled")
        withContext(Dispatchers.Main.immediate) {
            engine.masterPlayer.shuffleModeEnabled = enabled
            state.updateStablePlayerState { it.copy(isShuffleEnabled = enabled) }
        }
        McpToolResult.ok(if (enabled) "已开启随机播放。" else "已关闭随机播放。")
    }

    private fun playerSetFavorite(): McpTool = tool(
        "player_set_favorite",
        "收藏/取消收藏一首本地歌曲",
        McpScopes.WRITE_FAVORITE,
        objectSchema(
            mapOf(
                "songId" to stringProp("歌曲 ID"),
                "favorite" to boolProp("true 收藏，false 取消"),
            ),
            listOf("songId", "favorite"),
        ),
    ) { args ->
        val songId = args.string("songId") ?: return@tool McpToolResult.fail("缺少 songId")
        val favorite = args.bool("favorite") ?: return@tool McpToolResult.fail("缺少 favorite")
        music.setFavoriteStatus(songId, favorite)
        McpToolResult.ok(if (favorite) "已收藏。" else "已取消收藏。")
    }

    // ---- library ----

    private fun librarySearch(): McpTool = tool(
        "library_search",
        "搜索本地曲库。type 支持 song / playlist，默认 song。",
        McpScopes.READ_LIBRARY,
        objectSchema(
            mapOf(
                "query" to stringProp("搜索关键词"),
                "type" to enumProp("搜索类型", listOf("song", "playlist")),
                "limit" to intProp("最多返回条数", minimum = 1, maximum = 50, default = 20),
            ),
            listOf("query"),
        ),
    ) { args ->
        val query = args.string("query")?.trim().orEmpty()
        if (query.isBlank()) return@tool McpToolResult.fail("query 不能为空")
        val type = args.string("type") ?: "song"
        val limit = (args.int("limit") ?: 20).coerceIn(1, 50)
        when (type) {
            "song" -> {
                val songs = music.searchSongs(query).first().take(limit)
                McpToolResult.ok(
                    "找到 ${songs.size} 首本地歌曲。",
                    buildJsonObject {
                        put("items", buildJsonArray { songs.forEach { add(it.toMcpJson()) } })
                    },
                )
            }
            "playlist" -> {
                val list = music.searchPlaylists(query).take(limit)
                McpToolResult.ok(
                    "找到 ${list.size} 个歌单。",
                    buildJsonObject {
                        put("items", buildJsonArray {
                            list.forEach { add(buildJsonObject {
                                put("id", it.id)
                                put("name", it.name)
                                put("count", it.songIds.size)
                            }) }
                        })
                    },
                )
            }
            else -> McpToolResult.fail("当前版本仅支持 song / playlist 搜索")
        }
    }

    private fun libraryGetSong(): McpTool = tool(
        "library_get_song",
        "按 ID 读取一首本地歌曲的元数据。",
        McpScopes.READ_LIBRARY,
        objectSchema(mapOf("songId" to stringProp("歌曲 ID")), listOf("songId")),
    ) { args ->
        val id = args.string("songId") ?: return@tool McpToolResult.fail("缺少 songId")
        val song = music.getSong(id).first()
            ?: return@tool McpToolResult.fail("没有找到该歌曲")
        McpToolResult.ok("《${song.title}》- ${song.displayArtist}", buildJsonObject {
            put("song", song.toMcpJson())
        })
    }

    private fun libraryListPlaylists(): McpTool = tool(
        "library_list_playlists",
        "列出本地歌单。",
        McpScopes.READ_LIBRARY,
        objectSchema(mapOf("limit" to intProp("最多返回条数", minimum = 1, maximum = 100, default = 50)), emptyList()),
    ) { args ->
        val limit = (args.int("limit") ?: 50).coerceIn(1, 100)
        val list = playlists.userPlaylistsFlow.first().take(limit)
        McpToolResult.ok("共 ${list.size} 个歌单。", buildJsonObject {
            put("items", buildJsonArray {
                list.forEach { add(buildJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                    put("count", it.songIds.size)
                }) }
            })
        })
    }

    private fun libraryGetPlaylist(): McpTool = tool(
        "library_get_playlist",
        "读取歌单详情与歌曲列表。",
        McpScopes.READ_LIBRARY,
        objectSchema(mapOf("playlistId" to stringProp("歌单 ID")), listOf("playlistId")),
    ) { args ->
        val id = args.string("playlistId") ?: return@tool McpToolResult.fail("缺少 playlistId")
        val playlist = playlists.userPlaylistsFlow.first().firstOrNull { it.id == id }
            ?: return@tool McpToolResult.fail("没有找到该歌单")
        val songs = if (playlist.songIds.isEmpty()) emptyList()
        else music.getSongsByIds(playlist.songIds).first()
        McpToolResult.ok("歌单《${playlist.name}》共 ${songs.size} 首。", buildJsonObject {
            put("id", playlist.id)
            put("name", playlist.name)
            put("items", buildJsonArray { songs.forEach { add(it.toMcpJson()) } })
        })
    }

    private fun libraryCreatePlaylist(): McpTool = tool(
        "library_create_playlist",
        "用真实存在的本地歌曲 ID 创建歌单。songIds 必须来自搜索或候选结果。",
        McpScopes.WRITE_PLAYLIST,
        objectSchema(
            mapOf(
                "name" to stringProp("歌单名称"),
                "songIds" to arrayProp("歌曲 ID 列表"),
            ),
            listOf("name", "songIds"),
        ),
    ) { args ->
        val name = args.string("name")?.trim().orEmpty()
        if (name.isBlank()) return@tool McpToolResult.fail("name 不能为空")
        val ids = args.stringList("songIds").distinct()
        if (ids.isEmpty()) return@tool McpToolResult.fail("songIds 不能为空")
        val playlist = playlists.createPlaylist(name = name, songIds = ids, isAiGenerated = true)
        McpToolResult.ok("已创建歌单《${playlist.name}》，共 ${ids.size} 首。", buildJsonObject {
            put("playlistId", playlist.id)
            put("name", playlist.name)
            put("count", ids.size)
        })
    }

    private fun libraryAddToPlaylist(): McpTool = tool(
        "library_add_to_playlist",
        "把本地歌曲加入已有歌单。",
        McpScopes.WRITE_PLAYLIST,
        objectSchema(
            mapOf(
                "playlistId" to stringProp("歌单 ID"),
                "songIds" to arrayProp("歌曲 ID 列表"),
            ),
            listOf("playlistId", "songIds"),
        ),
    ) { args ->
        val playlistId = args.string("playlistId") ?: return@tool McpToolResult.fail("缺少 playlistId")
        val ids = args.stringList("songIds").distinct()
        if (ids.isEmpty()) return@tool McpToolResult.fail("songIds 不能为空")
        playlists.addSongsToPlaylist(playlistId, ids)
        McpToolResult.ok("已加入 ${ids.size} 首歌曲。")
    }

    // ---- AI data ----

    private fun aiGetCandidates(): McpTool = tool(
        "ai_get_candidates",
        "返回本地曲库的个性化候选歌曲及打分，供客户端 LLM 自行选曲。",
        McpScopes.READ_LIBRARY,
        objectSchema(
            mapOf("limit" to intProp("候选数量", minimum = 1, maximum = 100, default = 60)),
            emptyList(),
        ),
    ) { args ->
        val limit = (args.int("limit") ?: 60).coerceIn(1, 100)
        val all = music.getAllSongsOnce()
        val candidates = dailyMix.getTopCandidatesForAi(all, emptySet(), limit)
        val items = buildJsonArray {
            candidates.forEach { song ->
                add(buildJsonObject {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.displayArtist)
                    put("album", song.album)
                    put("genre", song.genre)
                    put("year", song.year)
                    put("durationMs", song.duration)
                    put("favorite", song.isFavorite)
                    put("score", dailyMix.getScore(song.id))
                })
            }
        }
        McpToolResult.ok("已返回 ${candidates.size} 首候选。", buildJsonObject { put("items", items) })
    }

    private fun aiGetProfile(): McpTool = tool(
        "ai_get_profile",
        "返回用户听歌画像摘要（统计、常听、发现池），可直接注入客户端提示词。",
        McpScopes.READ_PROFILE,
        objectSchema(emptyMap(), emptyList()),
    ) {
        val all = music.getAllSongsOnce()
        val text = digest.generateDigest(all, isSafeLimit = true)
        McpToolResult.ok("已生成用户画像。", buildJsonObject { put("digest", text) })
    }

    // ---- online ----

    private fun onlineSearch(): McpTool = tool(
        "online_search",
        "把关键词并发搜索到在线音源（wy/kw/tx/kg/mg），返回候选歌曲。不会调用 LLM，也不返回播放直链。",
        McpScopes.READ_LIBRARY,
        objectSchema(
            mapOf(
                "query" to stringProp("搜索关键词，建议「歌名 - 歌手」"),
                "sources" to arrayProp("音源列表，默认 wy/kw/tx"),
                "page" to intProp("页码", minimum = 1, default = 1),
                "pageSize" to intProp("每源返回条数", minimum = 1, maximum = 30, default = 15),
            ),
            listOf("query"),
        ),
    ) { args ->
        val query = args.string("query")?.trim().orEmpty()
        if (query.isBlank()) return@tool McpToolResult.fail("query 不能为空")
        val sources = args.stringList("sources")
            .filter { it in setOf("wy", "kw", "tx", "kg", "mg") }
            .ifEmpty { listOf("wy", "kw", "tx") }
        val page = (args.int("page") ?: 1).coerceAtLeast(1)
        val pageSize = (args.int("pageSize") ?: 15).coerceIn(1, 30)

        val results: List<Pair<String, LxSearchResult>> = kotlinx.coroutines.coroutineScope {
            val scope = this
            sources.map { source ->
                scope.async(Dispatchers.IO) {
                    source to runCatching {
                        onlineSources.search(source, query, page, pageSize)
                    }.getOrDefault(LxSearchResult())
                }
            }.map { it.await() }
        }

        val items = buildJsonArray {
            results.forEach { (source, result) ->
                result.list.forEach { song ->
                    add(buildJsonObject {
                        put("source", song.source.ifBlank { source })
                        put("songId", song.id.ifBlank { song.songmid })
                        put("name", song.name)
                        put("singer", song.singer)
                        put("albumName", song.albumName)
                        put("durationMs", song.duration)
                        put("pic", song.pic)
                    })
                }
            }
        }
        McpToolResult.ok(
            "在线搜索「$query」返回 ${items.size} 条候选。",
            buildJsonObject {
                put("items", items)
                put("sources", buildJsonArray { sources.forEach { add(it) } })
            },
        )
    }

    // ---- lyrics ----

    private fun lyricsGet(): McpTool = tool(
        "lyrics_get",
        "读取指定歌曲（缺省为当前歌曲）的歌词，返回 LRC 文本。",
        McpScopes.READ_LYRICS,
        objectSchema(mapOf("songId" to stringProp("歌曲 ID，可省略")), emptyList()),
    ) { args ->
        val id = args.string("songId")
        val song = if (id.isNullOrBlank()) state.stablePlayerState.value.currentSong
        else music.getSong(id).first()
        if (song == null) return@tool McpToolResult.fail("没有找到歌曲")
        val lyrics = music.getLyrics(song)
            ?: return@tool McpToolResult.fail("没有获取到歌词")
        val text = lyrics.toLrc()
        McpToolResult.ok("已获取《${song.title}》歌词。", buildJsonObject {
            put("songId", song.id)
            put("format", "lrc")
            put("text", text.take(64_000))
        })
    }

    // ---- queue ----

    private fun queuePlay(): McpTool = tool(
        "queue_play",
        "用本地歌曲 ID 列表替换当前队列并开始播放。",
        McpScopes.CONTROL_PLAYBACK,
        objectSchema(
            mapOf(
                "songIds" to arrayProp("歌曲 ID 列表"),
                "startIndex" to intProp("从第几首开始", minimum = 0, default = 0),
                "queueName" to stringProp("队列名称，可省略"),
            ),
            listOf("songIds"),
        ),
    ) { args ->
        val ids = args.stringList("songIds").distinct()
        if (ids.isEmpty()) return@tool McpToolResult.fail("songIds 不能为空")
        val fetched = music.getSongsByIds(ids).first().associateBy { it.id }
        val ordered = ids.mapNotNull { fetched[it] }
        if (ordered.isEmpty()) return@tool McpToolResult.fail("没有可播放的歌曲")
        val start = (args.int("startIndex") ?: 0).coerceIn(0, ordered.lastIndex)
        withContext(Dispatchers.Main.immediate) {
            val items = ordered.map { MediaItemBuilder.build(it) }
            engine.masterPlayer.setMediaItems(items, start, 0L)
            engine.masterPlayer.prepare()
            engine.masterPlayer.play()
        }
        McpToolResult.ok("已开始播放 ${ordered.size} 首。", buildJsonObject {
            put("accepted", true)
            put("queueSize", ordered.size)
            put("startedSongId", ordered[start].id)
        })
    }

    // ---- app ----

    private fun appOpenSettings(): McpTool = tool(
        "app_open_settings",
        "在播放器中打开指定设置分类。category 必须是播放器支持的分类 ID。",
        McpScopes.CONTROL_SETTINGS,
        objectSchema(
            mapOf("category" to stringProp("设置分类 ID，例如 playback / ai_integration / about")),
            listOf("category"),
        ),
    ) { args ->
        val category = args.string("category")
            ?: return@tool McpToolResult.fail("缺少 category")
        if (SettingsCategory.entries.none { it.id == category }) {
            return@tool McpToolResult.fail("未知设置分类：$category")
        }
        McpUiCommandBus.post(McpUiCommand.OpenSettings(category))
        McpToolResult.ok("已打开设置：$category。")
    }

    private fun appNavigate(): McpTool = tool(
        "app_navigate",
        "跳转到播放器页面：home / search / library / ai_assistant / ai_mix / daily_mix / stats / equalizer。",
        McpScopes.CONTROL_SETTINGS,
        objectSchema(
            mapOf("screen" to enumProp("页面", listOf(
                "home", "search", "library", "ai_assistant", "ai_mix",
                "daily_mix", "stats", "equalizer",
            ))),
            listOf("screen"),
        ),
    ) { args ->
        val screen = args.string("screen") ?: return@tool McpToolResult.fail("缺少 screen")
        val route = when (screen) {
            "home" -> Screen.Home.route
            "search" -> Screen.Search.route
            "library" -> Screen.Library.route
            "ai_assistant" -> Screen.AiAssistant.route
            "ai_mix" -> Screen.AiMixScreen.route
            "daily_mix" -> Screen.DailyMixScreen.route
            "stats" -> Screen.Stats.route
            "equalizer" -> Screen.Equalizer.route
            else -> null
        } ?: return@tool McpToolResult.fail("未知页面：$screen")
        McpUiCommandBus.post(McpUiCommand.OpenScreen(route))
        McpToolResult.ok("已跳转到 $screen。")
    }

    private fun systemCapabilities(): McpTool = tool(
        "system_get_capabilities",
        "返回当前 MCP 服务支持的完整工具列表与协议版本。",
        McpScopes.READ_PLAYBACK,
        objectSchema(emptyMap(), emptyList()),
    ) {
        McpToolResult.ok(
            "共 ${tools.size} 个工具。",
            buildJsonObject {
                put("protocol", MCP_PROTOCOL_VERSION)
                put("server", MCP_SERVER_NAME)
                put("tools", buildJsonArray { tools.forEach { add(it.name) } })
            },
        )
    }

    // ---- helpers ----

    private fun simple(
        name: String,
        description: String,
        scope: String,
        handler: suspend (JsonObject) -> McpToolResult,
    ): McpTool = tool(name, description, scope, objectSchema(emptyMap(), emptyList()), handler)

    private fun tool(
        toolName: String,
        toolDescription: String,
        toolScope: String,
        schema: JsonObject,
        handler: suspend (JsonObject) -> McpToolResult,
    ): McpTool = object : McpTool {
        override val name = toolName
        override val description = toolDescription
        override val scope = toolScope
        override val inputSchema = schema
        override suspend fun invoke(args: JsonObject) = handler(args)
    }

    private fun Song.toMcpJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("artist", displayArtist)
        put("album", album)
        put("durationMs", duration)
        put("genre", genre)
        put("year", year)
        put("favorite", isFavorite)
    }

    private fun Lyrics.toLrc(): String = buildString {
        val synced = synced
        if (!synced.isNullOrEmpty()) {
            synced.forEach { line ->
                append('[').append(formatLrcTime(line.time)).append("] ")
                append(line.line)
                if (!line.translation.isNullOrBlank()) append(" / ").append(line.translation)
                append('\n')
            }
        } else {
            plain?.forEach { append(it).append('\n') }
        }
    }.trim()

    private fun formatLrcTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun objectSchema(
        properties: Map<String, JsonObject>,
        required: List<String>,
    ): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (key, value) -> put(key, value) }
        }
        if (required.isNotEmpty()) {
            putJsonArray("required") { required.forEach { add(it) } }
        }
        put("additionalProperties", false)
    }

    private fun stringProp(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun enumProp(description: String, values: List<String>): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    private fun intProp(
        description: String,
        minimum: Int? = null,
        maximum: Int? = null,
        default: Int? = null,
    ): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
        default?.let { put("default", it) }
    }

    private fun numProp(
        description: String,
        minimum: Double? = null,
        maximum: Double? = null,
    ): JsonObject = buildJsonObject {
        put("type", "number")
        put("description", description)
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

    private fun boolProp(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    private fun arrayProp(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        putJsonObject("items") { put("type", "string") }
    }
}
