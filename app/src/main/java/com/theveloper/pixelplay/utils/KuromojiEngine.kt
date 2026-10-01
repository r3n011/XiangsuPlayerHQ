package com.theveloper.pixelplay.utils

import android.content.Context
import com.theveloper.pixelplay.PixelPlayApplication
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * kuromoji-ipadic 日语分词引擎的按需加载器。
 *
 * kuromoji（类 + ipadic 词典，APK 内约 12.7MB）不再打进安装包，改为用户首次需要
 * 日语罗马音时从 GitHub Release 下载自包含 jar（约 12.7MB，一次性），经
 * DexClassLoader 动态加载：
 *   - jar 内含 classes.dex（kuromoji-core + kuromoji-ipadic 的 d8 转换产物）与
 *     com/atilika/kuromoji/ipadic/ 下的 .bin 词典资源；
 *   - kuromoji 通过 Class#getResourceAsStream（即类自身加载器）读取词典，
 *     因此词典资源会随 DexClassLoader 正确解析（见 tools/kuromoji/README.md）；
 *   - 分词结果经反射桥接为 [KuromojiToken]，调用方不直接依赖 kuromoji 类型。
 *
 * 产物构建、SHA-256 回填与托管方式见 tools/kuromoji/README.md。
 * 引擎缺失或加载失败时调用方走优雅降级（仅日语罗马音不显示，无其它影响）。
 */
object KuromojiEngine {

    /** 引擎产物下载地址（GitHub Release，App 端会自动套用 ghproxy 镜像前缀） */
    private const val ENGINE_URL =
        "https://github.com/r3n011/XiangsuPlayerHQ/releases/download/kuromoji-engine-v1/kuromoji-ipadic-android.jar"

    /** 产物完整性校验（与 Release 的 jar 严格一致；升级产物后必须回填） */
    private const val ENGINE_SHA256 = "f338bc28da5d3311e822378b5fae9f5f3feb58600c4009fd62ea50d8adfbced5"
    private const val ENGINE_SIZE_BYTES = 13362754L

    private const val ENGINE_JAR_NAME = "kuromoji-ipadic-android.jar"
    private const val TOKENIZER_CLASS = "com.atilika.kuromoji.ipadic.Tokenizer"

    /** 下载失败后的重试间隔（罗马音触发频率高，避免每次打开歌词页都重试下载） */
    private const val RETRY_INTERVAL_MS = 10 * 60 * 1000L

    /** 加载失败后的冷却间隔：jar 已校验通过但不加载时不删除、不重下，冷却期内直接降级 */
    private const val LOAD_FAILURE_COOLDOWN_MS = 5 * 60 * 1000L

    /** 与 ApkDownloadInstaller 相同的 GitHub 加速镜像（顺序即尝试顺序，末尾追加官方直连） */
    private val mirrorPrefixes = listOf(
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
        "https://gh-proxy.com/",
        "https://ghproxy.homeboyc.cn/",
        "https://github.akams.cn/"
    )

    sealed interface EngineState {
        /** 引擎未安装，且当前没有下载任务 */
        data object NotInstalled : EngineState

        /** 正在下载（progress: 0-100，未知总长时为 -1） */
        data class Downloading(val progressPercent: Int) : EngineState

        /** 引擎可用（jar 已下载且校验通过；不代表 Tokenizer 已实例化） */
        data object Ready : EngineState

        data class Failed(val message: String) : EngineState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 正在执行的下载 Job：防止并发触发多个下载任务 */
    @Volatile
    private var downloadJob: Job? = null

    /** 最近一次加载失败时间：加载失败不删除已校验通过的 jar，只冷却重试 */
    @Volatile
    private var lastLoadFailureAt: Long = 0L

    private val _state by lazy {
        MutableStateFlow<EngineState>(if (engineJar().exists()) EngineState.Ready else EngineState.NotInstalled)
    }
    val state: StateFlow<EngineState> get() = _state.asStateFlow()

    /**
     * "分词器已成功初始化"的信号（每次加载成功都会发，含重复加载）。
     *
     * 为什么不能只监听 [state]：state 是 StateFlow，已就绪后再次加载成功不会产生新值，
     * 上层也就收不到通知（典型场景：jar 在、但首次加载失败进了冷却期，歌词已经按"无引擎"
     * 解析过了；用户手动重试加载成功后 state 仍等于 Ready，不会有任何刷新）。
     * replay = 1 让晚订阅的收集者也能拿到一次信号（歌词此时若还没加载，重算会安全地空跑）。
     */
    private val _readyEvents = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val readyEvents: SharedFlow<Unit> get() = _readyEvents.asSharedFlow()

    @Volatile
    private var tokenizerInstance: Any? = null

    @Volatile
    private var lastDownloadAttemptAt: Long = 0L

    /** 是否已对"已存在的 jar"做过一次完整性自检（每个进程一次） */
    @Volatile
    private var integrityChecked = false

    // ── 对外入口（LyricsUtils 使用） ─────────────────────────────────────

    /**
     * 返回已初始化的 kuromoji Tokenizer；引擎未安装/加载失败时触发后台下载并返回 null。
     * 返回 null 时调用方应优雅降级（本次不显示罗马音）。
     */
    fun obtainTokenizer(): Any? {
        tokenizerInstance?.let { return it }
        verifyInstalledAsync()
        val jar = engineJar()
        if (jar.exists()) {
            // 加载失败有冷却期：避免每次调用都反复尝试加载（且不触发重新下载）
            if (lastLoadFailureAt > 0L && System.currentTimeMillis() - lastLoadFailureAt < LOAD_FAILURE_COOLDOWN_MS) {
                return null
            }
            return loadTokenizer()
        }
        ensureEngineAvailableAsync()
        return null
    }

    /** 引擎是否已下载就绪（不触发加载） */
    fun isInstalled(): Boolean = engineJar().exists()

    /** 引擎文件是否存在但损坏（校验不匹配） */
    fun verifyInstalledJar(): Boolean {
        val jar = engineJar()
        if (!jar.exists()) return false
        val length = jar.length()
        if (length != ENGINE_SIZE_BYTES) {
            Timber.w("KuromojiEngine jar size %d != expected %d", length, ENGINE_SIZE_BYTES)
        }
        return runCatching { sha256Of(jar) == ENGINE_SHA256 }.getOrDefault(false)
    }

    // ── 下载 ─────────────────────────────────────────────────────────────

    fun ensureEngineAvailableAsync() {
        val current = _state.value
        if (current is EngineState.Downloading || current is EngineState.Ready) return
        val now = System.currentTimeMillis()
        if (current is EngineState.Failed && now - lastDownloadAttemptAt < RETRY_INTERVAL_MS) return
        // 并发防护：已有下载任务在跑时不再启动第二个
        if (downloadJob?.isActive == true) return
        lastDownloadAttemptAt = now
        downloadJob = scope.launch { downloadEngine() }
    }

    /**
     * 完整性自检：jar 存在但损坏（SHA 不匹配）时删除并回到"未安装"。
     * 只做一次（每个进程），且**不触发下载**——避免启动即下载 13MB。
     * 供设置页 / 首次使用引擎时调用，用于纠正"显示已安装但其实用不了"。
     */
    fun verifyInstalledAsync() {
        if (integrityChecked) return
        integrityChecked = true
        if (!engineJar().exists()) return
        scope.launch {
            if (!verifyInstalledJar()) {
                Timber.w("KuromojiEngine installed jar is corrupt; removing")
                engineJar().delete()
                tokenizerInstance = null
                _state.value = EngineState.NotInstalled
            }
        }
    }

    /**
     * 设置页"立即下载/重试"入口：尽最大努力让引擎真正可用。
     *
     * - 已有 jar 且校验通过：先就地重新加载（可能是上次加载失败后的残留），成功即就绪；
     * - jar 损坏、或已校验的文件仍加载失败：删除后重新下载，
     *   避免永久卡在"看起来装了但用不了"；
     * - 未安装：直接下载。
     */
    fun requestDownloadNow() {
        lastDownloadAttemptAt = 0L
        lastLoadFailureAt = 0L
        if (downloadJob?.isActive == true) return
        lastDownloadAttemptAt = System.currentTimeMillis()
        downloadJob = scope.launch { downloadEngine(reuseExistingJarIfUsable = true) }
    }

    private suspend fun downloadEngine(reuseExistingJarIfUsable: Boolean = false) {
        try {
            val dest = engineJar()

            // 手动重试：已有 jar 先就地复用，避免无脑重下 13MB
            if (reuseExistingJarIfUsable && dest.exists()) {
                if (verifyInstalledJar()) {
                    if (loadTokenizer() != null) {
                        _state.value = EngineState.Ready
                        Timber.i("KuromojiEngine existing jar is usable; skip download")
                        return
                    }
                    // 校验通过但加载失败：最常见原因是 dexopt 产物缓存损坏 → 清缓存重试一次
                    Timber.w("KuromojiEngine load failed; clearing dexopt cache and retrying")
                    tokenizerInstance = null
                    lastLoadFailureAt = 0L
                    File(PixelPlayApplication.appContext().codeCacheDir, "kuromoji").deleteRecursively()
                    if (loadTokenizer() != null) {
                        _state.value = EngineState.Ready
                        return
                    }
                    // 同一份产物重新下载也不会得到不同结果，如实上报，不再浪费 13MB 流量
                    _state.value = EngineState.Failed("engine cannot be loaded on this device")
                    return
                }
                Timber.w("KuromojiEngine existing jar is corrupt; re-downloading")
                dest.delete()
                tokenizerInstance = null
                lastLoadFailureAt = 0L
            }

            _state.value = EngineState.Downloading(-1)
            val part = File(dest.parentFile, dest.name + ".part")
            part.parentFile?.mkdirs()

            val candidates = mirrorPrefixes.map { it + ENGINE_URL } + ENGINE_URL
            var lastError: Exception? = null
            for (candidate in candidates) {
                try {
                    downloadTo(candidate, part)
                    break
                } catch (e: Exception) {
                    lastError = e
                    Timber.w(e, "KuromojiEngine download failed from %s", candidate)
                    part.delete()
                }
            }

            if (!part.exists()) {
                throw (lastError ?: IOException("all download candidates failed"))
            }

            val actualSha = sha256Of(part)
            if (actualSha != ENGINE_SHA256) {
                part.delete()
                throw IOException("sha256 mismatch: expected=$ENGINE_SHA256 actual=$actualSha")
            }
            if (!part.renameTo(dest)) {
                dest.delete()
                if (!part.renameTo(dest)) throw IOException("cannot move downloaded jar into place")
            }
            // ⚡ 落盘后立刻置为只读：Android 14+ 不允许加载可写的 dex 文件（见 loadTokenizer）
            ensureReadOnly(dest)
            _state.value = EngineState.Ready
            Timber.i("KuromojiEngine installed (%d bytes)", dest.length())

            // 预热加载：把"下载成功但实际无法加载"当场暴露出来，
            // 否则用户要等到打开日语歌词页（且没人通知 UI 刷新）才发现没反应。
            if (loadTokenizer() == null) {
                _state.value = EngineState.Failed("downloaded engine cannot be loaded")
            }
        } catch (e: Exception) {
            _state.value = EngineState.Failed(e.message ?: "download failed")
            Timber.w(e, "KuromojiEngine download failed")
        } finally {
            downloadJob = null
        }
    }

    private fun downloadTo(url: String, dest: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PixelPlay-KuromojiEngine")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastProgressUpdate = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastProgressUpdate > 300) {
                            lastProgressUpdate = now
                            _state.value = EngineState.Downloading(((written * 100) / total).toInt())
                        }
                    }
                    if (total > 0 && written != total) {
                        throw IOException("truncated download: $written / $total")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    // ── 加载与反射桥接 ───────────────────────────────────────────────────

    /**
     * Android 14（API 34）起，`DexClassLoader` 拒绝加载**可写**的 dex/jar：
     * `SecurityException: Writable dex file '...' is not allowed.`
     * 下载落盘的文件默认可写，所以加载前必须把它置为只读（幂等）。
     */
    private fun ensureReadOnly(file: File) {
        runCatching {
            if (file.exists() && file.canWrite()) file.setReadOnly()
        }.onFailure { Timber.w(it, "KuromojiEngine: 无法把引擎 jar 置为只读") }
    }

    private fun loadTokenizer(): Any? {
        synchronized(this) {
            tokenizerInstance?.let { return it }
            val jar = engineJar()
            if (!jar.exists()) return null
            if (!verifyInstalledJar()) {
                Timber.w("KuromojiEngine jar corrupted, re-downloading")
                jar.delete()
                _state.value = EngineState.NotInstalled
                ensureEngineAvailableAsync()
                return null
            }
            // ⚡ Android 14（API 34）起禁止加载「可写」的 dex/jar 文件，否则抛
            //   SecurityException: Writable dex file '...' is not allowed.
            //   下载落盘的文件默认可写，这里加载前强制置为只读（幂等，已只读时直接跳过）。
            ensureReadOnly(jar)
            return try {
                val context: Context = PixelPlayApplication.appContext()
                val optimizedDir = File(context.codeCacheDir, "kuromoji").apply { mkdirs() }
                val loader = DexClassLoader(
                    jar.absolutePath,
                    optimizedDir.absolutePath,
                    null,
                    KuromojiEngine::class.java.classLoader
                )
                val tokenizerClass = Class.forName(TOKENIZER_CLASS, true, loader)
                val instance = tokenizerClass.getDeclaredConstructor().newInstance()
                tokenizerInstance = instance
                lastLoadFailureAt = 0L
                Timber.i("KuromojiEngine tokenizer initialized")
                // 通知上层：引擎现在真的能用了，可以补算之前算不出罗马音的歌词
                _readyEvents.tryEmit(Unit)
                instance
            } catch (t: Throwable) {
                Timber.w(t, "KuromojiEngine load failed; will retry after cooldown")
                // ⚡ 已校验通过的 jar 不再删除：删除会触发重新下载，导致每次打开日语歌都重下。
                // 只记录失败时间，冷却期内 obtainTokenizer 直接降级返回 null。
                lastLoadFailureAt = System.currentTimeMillis()
                null
            }
        }
    }

    /**
     * 反射调用 tokenizer.tokenize(text)，把 kuromoji Token 桥接为 [KuromojiToken]。
     * 任一步骤失败返回 null（调用方降级）。
     */
    fun tokenize(tokenizer: Any, text: String): List<KuromojiToken>? = try {
        val tokenize = tokenizer.javaClass.getMethod("tokenize", String::class.java)
        val raw = tokenize.invoke(tokenizer, text) as? List<*>
        raw?.mapNotNull { token ->
            token ?: return@mapNotNull null
            KuromojiToken(
                surface = stringGetter(token, "getSurface"),
                // 读音优先取 getReading；部分 token（记号/未知词）没有 reading 时退回发音
                reading = stringGetter(token, "getReading") ?: stringGetter(token, "getPronunciation"),
                pos1 = stringGetter(token, "getPartOfSpeechLevel1"),
                pos2 = stringGetter(token, "getPartOfSpeechLevel2")
            )
        }
    } catch (t: Throwable) {
        Timber.w(t, "KuromojiEngine tokenize failed")
        null
    }

    private fun stringGetter(target: Any, method: String): String? = try {
        (target.javaClass.getMethod(method).invoke(target) as? String)
    } catch (t: Throwable) {
        Timber.w(t, "KuromojiEngine getter %s failed", method)
        null
    }

    // ── 工具 ─────────────────────────────────────────────────────────────

    private fun engineJar(): File {
        val context: Context = PixelPlayApplication.appContext()
        return File(context.filesDir, "kuromoji/$ENGINE_JAR_NAME")
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** kuromoji Token 的最小桥接视图（只暴露罗马音转换用到的字段） */
data class KuromojiToken(
    val surface: String?,
    val reading: String?,
    val pos1: String?,
    val pos2: String?
)
