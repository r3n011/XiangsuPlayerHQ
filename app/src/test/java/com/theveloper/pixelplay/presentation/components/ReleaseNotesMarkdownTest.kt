package com.theveloper.pixelplay.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReleaseNotesMarkdownTest {

    @Test
    fun `html img is converted to markdown image`() {
        val body = """
            本版本由guoyue2010（我）进行编写测试
            <img width="1755" height="515" alt="image" src="https://github.com/user-attachments/assets/915f6035-a38c-446e-bcd4-43c26c86e9f9" />
            本次更新内容：
            1、进行了音源测试
        """.trimIndent()

        val normalized = normalizeReleaseMarkdown(body)

        assertTrue(normalized.contains("![image](https://github.com/user-attachments/assets/915f6035-a38c-446e-bcd4-43c26c86e9f9)"))
        assertFalse(normalized.contains("<img"))
    }

    @Test
    fun `split keeps text before and after an image`() {
        val body = "更新前\n![](https://example.com/a.png)\n更新后"

        val blocks = splitReleaseNoteBlocks(body)

        assertEquals(3, blocks.size)
        assertEquals(ReleaseNoteBlock.Text("更新前"), blocks[0])
        assertEquals(ReleaseNoteBlock.Image("https://example.com/a.png", ""), blocks[1])
        assertEquals(ReleaseNoteBlock.Text("更新后"), blocks[2])
    }

    @Test
    fun `preview drops image markup`() {
        val body = "第一行\n<img src=\"https://example.com/shot.png\" />\n第二行"

        val preview = releaseNotesPreview(body)

        assertEquals("第一行\n第二行", preview)
        assertFalse(preview.contains("https://"))
        assertFalse(preview.contains("<img"))
    }

    @Test
    fun `markdown links and line breaks survive normalization`() {
        val body = "详见 <a href=\"https://example.com/list\">音源清单</a><br>共 100 条"

        val normalized = normalizeReleaseMarkdown(body)

        assertTrue(normalized.contains("[音源清单](https://example.com/list)"))
        assertTrue(normalized.contains("共 100 条"))
        assertFalse(normalized.contains("<a "))
        assertFalse(normalized.contains("<br"))
    }
}
