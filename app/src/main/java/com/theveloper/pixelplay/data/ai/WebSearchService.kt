package com.theveloper.pixelplay.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** 一条 Bing 搜索结果。 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * 基于必应网页搜索结果的三方轻量联网搜索（无 API Key）。
 *
 * 直接抓取 `https://www.bing.com/search` 的 HTML，解析 `b_algo` 结果块得到
 * 标题 / 链接 / 摘要，供 AI 助手做科普、背景补充等联网能力使用。
 * 解析足够健壮：抓不到字段时跳过该条，极端情况返回空列表由上层兜底提示。
 */
@Singleton
class WebSearchService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WebSearch"
        private const val BING_URL = "https://www.bing.com/search"
        private val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private val TAG_RE = Regex("<[^>]*>")
        private val ENTITY_RE = Regex("&(nbsp|amp|lt|gt|quot|#39|apos);")
    }

    /**
     * 搜索并返回前 [count] 条结果；请求失败或无结果时返回空列表。
     */
    suspend fun search(query: String, count: Int = 5): List<WebSearchResult> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BING_URL?q=${URLEncoder.encode(query, "UTF-8")}&setlang=zh-hans&mkt=zh-CN"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build()
                val body = runCatching {
                    okHttpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) null else resp.body?.string()
                    }
                }.getOrNull() ?: return@withContext emptyList()
                parseResults(body, count)
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "search failed: $query")
                emptyList()
            }
        }
    }

    /** 从必应 HTML 中抽取 b_algo 结果块。 */
    private fun parseResults(html: String, count: Int): List<WebSearchResult> {
        val result = ArrayList<WebSearchResult>()
        // 用 b_algo 作为切分点逐块解析（parts[0] 是前导内容，跳过）
        val parts = html.split("<li class=\"b_algo\"")
        for (i in 1 until parts.size) {
            if (result.size >= count) break
            val block = parts[i]
            val anchor = extractAnchor(block) ?: continue
            val snippet = extractSnippet(block)
            result.add(WebSearchResult(anchor.title, anchor.url, snippet))
        }
        return result
    }

    private data class Anchor(val title: String, val url: String)

    /** 从结果块里取第一个裸 http 链接及其锚文本。 */
    private fun extractAnchor(block: String): Anchor? {
        val marker = "<a href=\"http"
        val aStart = block.indexOf(marker)
        if (aStart < 0) return null
        val hrefStart = aStart + marker.length
        val hrefEnd = block.indexOf('"', hrefStart)
        if (hrefEnd < 0) return null
        val url = block.substring(hrefStart, hrefEnd)

        val gt = block.indexOf('>', aStart)
        val aClose = block.indexOf("</a>", aStart)
        if (gt < 0 || aClose <= gt) return null
        val title = cleanText(block.substring(gt + 1, aClose))
        if (url.isBlank() || title.isBlank()) return null
        return Anchor(title, url)
    }

    private fun extractSnippet(block: String): String {
        val pStart = block.indexOf("<p")
        if (pStart < 0) return ""
        val pEnd = block.indexOf("</p>", pStart)
        if (pEnd < 0) return ""
        return cleanText(block.substring(pStart, pEnd))
    }

    /** 去除 HTML 标签、还原常用实体、压缩空白。 */
    private fun cleanText(raw: String): String {
        var s = TAG_RE.replace(raw, " ")
        s = ENTITY_RE.replace(s) {
            when (it.groupValues[1]) {
                "nbsp" -> " "
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos", "#39" -> "'"
                else -> it.value
            }
        }
        return s.replace(Regex("[\\s\u00a0]+"), " ").trim()
    }
}