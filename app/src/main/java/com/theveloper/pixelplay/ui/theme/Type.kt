package com.theveloper.pixelplay.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import java.io.File

const val CUSTOM_FONT_PREFIX = "CUSTOM:"
private const val FONTS_DIR_NAME = "fonts"

/**
 * 可按需下载的歌词内置字体。
 * 文件存于 filesDir/fonts/，首次选用时自动从 GitHub raw 下载（约4-6MB），之后离线可用。
 */
data class DownloadableFont(
    val key: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String
)

private val DOWNLOADABLE_FONTS = listOf(
    // 站酷系/马善政走 jsDelivr 托管 Google Fonts 官方文件（国内可达）；
    // 得意黑官方只发布 release zip，下载后需解压提取 ttf。
    DownloadableFont("ZCOOL_KUAILE", "站酷快乐体", "zcool_kuaile.ttf",
        "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/zcoolkuaile/ZCOOLKuaiLe-Regular.ttf"),
    DownloadableFont("ZCOOL_XIAOWEI", "站酷小薇", "zcool_xiaowei.ttf",
        "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/zcoolxiaowei/ZCOOLXiaoWei-Regular.ttf"),
    DownloadableFont("MA_SHAN_ZHENG", "马善政楷书", "ma_shan_zheng.ttf",
        "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/mashanzheng/MaShanZheng-Regular.ttf"),
    DownloadableFont("SMILEY_SANS", "得意黑", "smiley_sans.ttf",
        "https://github.com/atelier-anchor/smiley-sans/releases/download/v2.0.1/smiley-sans-v2.0.1.zip"),
)

fun downloadableFontForKey(key: String): DownloadableFont? =
    DOWNLOADABLE_FONTS.find { it.key == key.uppercase() }

fun isDownloadableFontKey(key: String): Boolean =
    downloadableFontForKey(key) != null

/** 检查可下载字体是否已下载到本地 filesDir/fonts/ */
fun isDownloadableFontDownloaded(context: Context, key: String): Boolean {
    val dl = downloadableFontForKey(key) ?: return false
    return File(getCustomFontsDir(context), dl.fileName).exists()
}

/**
 * 下载可下载字体到 filesDir/fonts/，已存在则跳过。
 * ⚡ suspend fun，内部切到 Dispatchers.IO，不会阻塞主线程。
 * - jsDelivr 直链（站酷系/马善政）国内可达，直接下载；
 * - GitHub release zip（得意黑）按镜像加速 + 官方原地址逐次尝试，下载后解压提取 ttf。
 * @return true=下载成功或已存在，false=下载失败
 */
suspend fun downloadLyricsFont(context: Context, key: String): Boolean {
    val dl = downloadableFontForKey(key) ?: return false
    val targetFile = File(getCustomFontsDir(context), dl.fileName)
    if (targetFile.exists()) return true
    val dir = getCustomFontsDir(context)
    if (!dir.exists()) dir.mkdirs()

    val isZipSource = dl.downloadUrl.lowercase().endsWith(".zip")
    // jsDelivr 是 CDN 无需镜像；GitHub release/raw 走镜像加速 + 官方兜底
    val candidates = when {
        dl.downloadUrl.startsWith("https://cdn.jsdelivr.net/") -> listOf(dl.downloadUrl)
        dl.downloadUrl.startsWith("https://") ->
            FONT_DOWNLOAD_MIRRORS.map { it + dl.downloadUrl } + dl.downloadUrl
        else -> listOf(dl.downloadUrl)
    }

    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        for (candidateUrl in candidates) {
            try {
                val conn = (java.net.URL(candidateUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    addRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                }
                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    conn.disconnect()
                    continue
                }
                val contentType = conn.contentType.orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    conn.disconnect()
                    continue
                }
                val success = if (isZipSource) {
                    // 下载 zip 到临时文件，再解压提取 ttf
                    val tmpZip = File(dir, "dl_${System.nanoTime()}.zip")
                    try {
                        conn.inputStream.use { input -> tmpZip.outputStream().use { it.write(input.readBytes()) } }
                        extractTtfFromZip(tmpZip, targetFile)
                    } finally {
                        tmpZip.delete()
                    }
                } else {
                    conn.inputStream.use { input ->
                        targetFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    targetFile.exists() && targetFile.length() > 1024
                }
                conn.disconnect()
                if (success) return@withContext true
                targetFile.delete()
            } catch (_: Exception) {
                targetFile.delete()
                // 继续尝试下一个候选链接
            }
        }
        false
    }
}

/** 从 zip 中提取第一个 .ttf 到目标文件，成功且体积有效返回 true */
private fun extractTtfFromZip(zipFile: File, target: File): Boolean {
    return try {
        java.util.zip.ZipInputStream(zipFile.inputStream(), Charsets.UTF_8).use { zis ->
            var entry = zis.nextEntry
            var found = false
            while (entry != null && !found) {
                if (!entry.isDirectory && entry.name.endsWith(".ttf", ignoreCase = true)) {
                    target.outputStream().use { out -> zis.copyTo(out) }
                    found = true
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            found && target.exists() && target.length() > 1024
        }
    } catch (_: Exception) {
        target.delete()
        false
    }
}

/** 字体下载的 GitHub 加速镜像（顺序即尝试顺序，官方原地址自动补在末尾兜底） */
private val FONT_DOWNLOAD_MIRRORS = listOf(
    "https://ghproxy.net/",
    "https://mirror.ghproxy.com/",
    "https://gh-proxy.com/",
    "https://ghproxy.homeboyc.cn/",
    "https://github.akams.cn/"
)

fun isCustomFontKey(key: String): Boolean = key.startsWith(CUSTOM_FONT_PREFIX)

fun customFontFileName(key: String): String = key.removePrefix(CUSTOM_FONT_PREFIX)

fun getCustomFontsDir(context: Context): File = File(context.filesDir, FONTS_DIR_NAME)

fun listCustomFonts(context: Context): List<String> {
    val dir = getCustomFontsDir(context)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()
        ?.filter { it.isFile && (it.extension.equals("ttf", true) || it.extension.equals("otf", true)) }
        ?.map { it.name }
        ?: emptyList()
}

fun customFontFamily(context: Context, key: String): FontFamily? {
    val fileName = customFontFileName(key)
    if (fileName.isBlank()) return null
    val fontFile = File(getCustomFontsDir(context), fileName)
    if (!fontFile.exists()) return null
    return try {
        FontFamily(
            androidx.compose.ui.text.font.Font(
                file = fontFile,
                weight = FontWeight.Normal
            )
        )
    } catch (e: Exception) {
        null
    }
}

fun customFontDisplayName(key: String): String {
    val fileName = customFontFileName(key)
    return fileName.removeSuffix(".ttf").removeSuffix(".otf")
}

/**
 * 删除自定义字体文件（应用内部存储），返回是否删除成功。
 */
fun deleteCustomFont(context: Context, key: String): Boolean {
    val fileName = customFontFileName(key)
    if (fileName.isBlank()) return false
    val fontFile = File(getCustomFontsDir(context), fileName)
    return fontFile.exists() && fontFile.isFile && fontFile.delete()
}

private val montserrat = GoogleFont("Montserrat")
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

val MontserratFamily = FontFamily(
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Black),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Light),
)

val ExpTitleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 60.sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.5f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    displayMedium = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 50.sp,
        //textGeometricTransform = TextGeometricTransform(scaleX = 1f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleMedium = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.3f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
)

// Google Sans Flex variable font with rounded axis for Google Sans Rounded-like appearance.
private const val GoogleSansFlexRond = 100f

@OptIn(ExperimentalTextApi::class)
val GoogleSansRounded = FontFamily(
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Light.weight),
            FontVariation.Setting("ROND", GoogleSansFlexRond)
        )
    ),
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight),
            FontVariation.Setting("ROND", GoogleSansFlexRond)
        )
    ),
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Medium.weight),
            FontVariation.Setting("ROND", GoogleSansFlexRond)
        )
    ),
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.SemiBold.weight),
            FontVariation.Setting("ROND", GoogleSansFlexRond)
        )
    ),
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Bold.weight),
            FontVariation.Setting("ROND", GoogleSansFlexRond)
        )
    ),
)

// Tomato-style Google Sans Flex families for the focus mode screen.
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexNormal = FontFamily(
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("ROND", GoogleSansFlexRond)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexEmphasized = FontFamily(
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("ROND", GoogleSansFlexRond)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexTopBarTitle = FontFamily(
    androidx.compose.ui.text.font.Font(
        resId = R.font.gflex_variable,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(900),
            FontVariation.width(112.5f),
            FontVariation.Setting("ROND", 35f)
        )
    )
)

/**
 * 内置中文字体定义已移除（R.font.* 改为按需下载，见 DOWNLOADABLE_FONTS）。
 * 旧定义（ZcoolKuaileFamily, ZcoolXiaoWeiFamily, MaShanZhengFamily, SmileySansFamily）
 * 的 FontFamily 已不再需要，由 resolveLyricsFontFamily(context, key) 按需加载。
 */

/**
 * 歌词字体选项 — 名称 → FontFamily 的映射。
 * "DEFAULT" → 跟随应用主题（Google Sans Rounded）。
 * 中文可下载字体（ZCOOL_KUAILE 等）的 FontFamily 由 resolveLyricsFontFamily(context, key) 按需加载。
 */
val LyricsFontFamilies: Map<String, FontFamily> = mapOf(
    "DEFAULT" to GoogleSansRounded,
    "MONTSERRAT" to MontserratFamily,
    "SYSTEM_DEFAULT" to FontFamily.Default,
    "SERIF" to FontFamily.Serif,
    "SANS_SERIF" to FontFamily.SansSerif,
    "MONOSPACE" to FontFamily.Monospace,
    "CURSIVE" to FontFamily.Cursive,
)

/**
 * 根据持久化的名称解析歌词字体，"DEFAULT" 或未知值都回落到主题字体。
 */
fun resolveLyricsFontFamily(name: String): FontFamily =
    LyricsFontFamilies[name.uppercase()] ?: GoogleSansRounded

/**
 * 支持自定义字体文件的解析。返回 Pair: (FontFamily?, isCustom)
 * 同时支持可下载字体：文件存在时从本地加载，不存在时回落默认字体（UI 侧可触发下载）。
 */
fun resolveLyricsFontFamily(context: Context, key: String): FontFamily {
    if (isCustomFontKey(key)) {
        return customFontFamily(context, key) ?: GoogleSansRounded
    }
    // 可下载字体：从 filesDir 加载已下载的文件，未下载时回落默认字体（UI 侧可触发下载）
    val dl = downloadableFontForKey(key)
    if (dl != null) {
        val fontFile = File(getCustomFontsDir(context), dl.fileName)
        if (fontFile.exists()) {
            return try {
                FontFamily(
                    androidx.compose.ui.text.font.Font(
                        file = fontFile,
                        weight = FontWeight.Normal
                    )
                )
            } catch (_: Exception) {
                GoogleSansRounded
            }
        }
        return GoogleSansRounded // 未下载时回落默认
    }
    return LyricsFontFamilies[key.uppercase()] ?: GoogleSansRounded
}

/**
 * 字体选项的显示名称（供 UI 展示）。
 * 可下载字体的显示名来自 DOWNLOADABLE_FONTS。
 */
val LyricsFontDisplayNames: Map<String, String> = mapOf(
    "DEFAULT" to "主题默认",
    "MONTSERRAT" to "Montserrat",
    "SYSTEM_DEFAULT" to "系统默认",
    "SERIF" to "衬线",
    "SANS_SERIF" to "无衬线",
    "MONOSPACE" to "等宽",
    "CURSIVE" to "手写",
) + DOWNLOADABLE_FONTS.associate { it.key to it.displayName }

// Tipografía - Usar fuentes amigables y modernas.
// Considerar añadir fuentes personalizadas en res/font para un look más único.
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
