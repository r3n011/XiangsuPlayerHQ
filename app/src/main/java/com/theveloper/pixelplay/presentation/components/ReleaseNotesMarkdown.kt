package com.theveloper.pixelplay.presentation.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noties.markwon.Markwon

/**
 * GitHub Release 正文里常见的 HTML 片段 → Markdown 归一化。
 *
 * Markwon（以及任何纯 Markdown 渲染器）不会解析 `<img>`，仓库作者又习惯用
 * `<img width=… src="https://github.com/user-attachments/assets/…" />` 贴图，
 * 所以此前更新日志 / 音源市场的图片一律看不到。这里把 HTML 图片、超链接、
 * 换行等标签改写成标准 Markdown，图片就能正常加载。
 */
private val HTML_IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_IMG_SRC = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val HTML_IMG_ALT = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val HTML_ANCHOR = Regex(
    """<a\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val MD_IMAGE = Regex(
    """!\[([^\]]*)]\(\s*(<[^>]+>|[^)\s]+)(?:\s+["'][^"']*["'])?\s*\)"""
)
private val LINKED_IMAGE = Regex("""\[!\[([^\]]*)]\(([^)\s]+)\)]\([^)]*\)""")

/** 图片链接里常见的 HTML 实体，顺手还原，否则 404 */
private fun decodeHtmlEntities(value: String): String = value
    .replace("&amp;", "&")
    .replace("&#38;", "&")
    .replace("&quot;", "\"")
    .trim()

fun normalizeReleaseMarkdown(raw: String): String {
    if (raw.isBlank()) return raw
    var text = raw

    // <img …/> → ![](…)
    text = HTML_IMG_TAG.replace(text) { match ->
        val tag = match.value
        val src = decodeHtmlEntities(
            HTML_IMG_SRC.find(tag)?.groupValues?.getOrNull(1).orEmpty()
        )
        if (src.isBlank()) {
            ""
        } else {
            val alt = HTML_IMG_ALT.find(tag)?.groupValues?.getOrNull(1).orEmpty()
                .replace("[", "").replace("]", "")
            "![$alt]($src)"
        }
    }

    // <a href="…">文字</a> → [文字](…)
    text = HTML_ANCHOR.replace(text) { match ->
        val href = decodeHtmlEntities(match.groupValues[1])
        val label = match.groupValues[2].trim()
        if (href.isBlank() || label.isBlank()) label else "[$label]($href)"
    }

    // <a> 包 <img> 时会变成 [![alt](img)](href)，嵌套图片在块渲染里会留下多余中括号
    text = LINKED_IMAGE.replace(text) { match ->
        "![${match.groupValues[1]}](${match.groupValues[2]})"
    }

    // 剩余的内联 HTML 只保留可读内容
    text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(Regex("""</?p\b[^>]*>""", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(
        Regex(
            """</?(?:div|details|summary|span|strong|b|em|i|code|pre|ul|ol|li|blockquote|table|thead|tbody|tr|td|th)\b[^>]*>""",
            RegexOption.IGNORE_CASE
        ),
        ""
    )
    return text.trim()
}

/** 更新日志正文中的一块内容：文本或图片 */
sealed interface ReleaseNoteBlock {
    data class Text(val text: String) : ReleaseNoteBlock
    data class Image(val url: String, val alt: String) : ReleaseNoteBlock
}

/**
 * 按图片位置把正文拆成「文本块 / 图片块」，供 Compose 原生渲染
 * （Markwon 的 TextView 太重，市场页还需要保持卡片点击展开的手感）。
 */
fun splitReleaseNoteBlocks(raw: String): List<ReleaseNoteBlock> {
    val normalized = normalizeReleaseMarkdown(raw)
    if (normalized.isBlank()) return emptyList()

    val blocks = mutableListOf<ReleaseNoteBlock>()
    var cursor = 0
    MD_IMAGE.findAll(normalized).forEach { match ->
        val text = normalized.substring(cursor, match.range.first)
            .trim('\n', '\r', ' ', '\t')
        if (text.isNotBlank()) blocks += ReleaseNoteBlock.Text(text)
        val url = decodeHtmlEntities(
            match.groupValues[2].removeSurrounding("<", ">")
        )
        if (url.isNotBlank()) {
            blocks += ReleaseNoteBlock.Image(url = url, alt = match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    val tail = normalized.substring(cursor).trim('\n', '\r', ' ', '\t')
    if (tail.isNotBlank()) blocks += ReleaseNoteBlock.Text(tail)
    return blocks
}

/** 折叠态预览：只留文字，避免把 Markdown / HTML 标签原样显示出来 */
fun releaseNotesPreview(raw: String): String =
    splitReleaseNoteBlocks(raw)
        .filterIsInstance<ReleaseNoteBlock.Text>()
        .joinToString("\n")
        .trim()

/**
 * 更新日志正文渲染：文本 + 图片。
 *
 * - [collapsed] 时只显示纯文字预览（图片收起，保持卡片紧凑）；
 * - 展开后逐块渲染，图片走 Coil，圆角裁切 + 高度上限，避免长图撑爆卡片。
 */
@Composable
fun ReleaseNotesBody(
    markdown: String,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    collapsedMaxLines: Int = 3,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (markdown.isBlank()) return
    val blocks = remember(markdown) { splitReleaseNoteBlocks(markdown) }
    if (blocks.isEmpty()) return

    if (collapsed) {
        val preview = remember(markdown) { releaseNotesPreview(markdown) }
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                style = textStyle,
                color = textColor,
                maxLines = collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNoteBlock.Text -> Text(
                    text = block.text,
                    style = textStyle,
                    color = textColor
                )
                is ReleaseNoteBlock.Image -> ReleaseNoteImage(block)
            }
        }
    }
}

/**
 * 更新日志 Markdown 渲染（关于页 / 更新日志弹窗用）：
 * 文本块交给 Markwon 保留标题、列表、粗体、链接等格式，图片块交给 Coil 加载。
 *
 * 之前整段正文塞进一个 TextView，既无法解析 GitHub 常见的 `<img>`，
 * 又因为直接 `tv.text = spannable` 跳过了 ImagesPlugin 的调度，图片永远加载不出来。
 */
@Composable
fun MarkdownReleaseNotesBody(
    markdown: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 14f,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    if (markdown.isBlank()) return
    val blocks = remember(markdown) { splitReleaseNoteBlocks(markdown) }
    if (blocks.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNoteBlock.Text -> MarkdownTextBlock(
                    text = block.text,
                    textSizeSp = textSizeSp,
                    textColor = textColor,
                    linkColor = linkColor
                )
                is ReleaseNoteBlock.Image -> ReleaseNoteImage(block)
            }
        }
    }
}

@Composable
private fun MarkdownTextBlock(
    text: String,
    textSizeSp: Float,
    textColor: Color,
    linkColor: Color
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.builder(context).build() }
    val spannable = remember(text) { markwon.toMarkdown(text) }
    val textArgb = textColor.toArgb()
    val linkArgb = linkColor.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = textSizeSp
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = android.graphics.Color.TRANSPARENT
            }
        },
        update = { tv ->
            tv.setTextColor(textArgb)
            tv.setLinkTextColor(linkArgb)
            markwon.setParsedMarkdown(tv, spannable)
        }
    )
}

@Composable
private fun ReleaseNoteImage(image: ReleaseNoteBlock.Image) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(image.url)
            .crossfade(true)
            .build(),
        contentDescription = image.alt.ifBlank { null },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 360.dp)
            .clip(RoundedCornerShape(12.dp)),
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
