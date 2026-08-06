@file:Suppress("NOTHING_TO_INLINE", "unused", "MemberVisibilityCanBePrivate")

package net.moriafly.ncm

/**
 * 极简 JSON 序列化/反序列化工具（0 外部依赖，纯 JDK）
 *
 * 只保证兼容网易云 API 用到的所有类型：
 *   Map<String, Any?>  → JSONObject
 *   List<Any?>         → JSONArray
 *   String / Number / Boolean / null → 原语
 *
 * 注意：
 *   - 不做完整 JSON 规范校验（例如 Unicode 转义），只保证 API 实际下发格式能正常工作
 *   - 数字优先解析为 Long，再按情况降级为 Int/Double
 */
object NcmJson {

    fun toJsonString(value: Any?): String = buildString { writeValue(value) }

    fun parseAny(text: String): Any? = Parser(text).parseValue()

    fun parseObject(text: String): Map<String, Any?> {
        val v = parseAny(text)
        @Suppress("UNCHECKED_CAST")
        return (v as? Map<String, Any?>) ?: emptyMap<String, Any?>()
    }

    fun parseArray(text: String): List<Any?> {
        val v = parseAny(text)
        @Suppress("UNCHECKED_CAST")
        return (v as? List<Any?>) ?: emptyList()
    }

    // ============================================================================
    // 序列化
    // ============================================================================

    private fun StringBuilder.writeValue(v: Any?) {
        when (v) {
            null -> append("null")
            is Map<*, *> -> writeObject(v as Map<String, Any?>)
            is List<*> -> writeArray(v)
            is Array<*> -> writeArray(v.asList())
            is CharSequence -> writeString(v.toString())
            is Char -> writeString(v.toString())
            is Boolean -> append(v)
            is Number -> append(v.toString())   // Long/Int/Double 原样输出
            is Enum<*> -> writeString(v.name)
            else -> writeString(v.toString())   // 兜底：toString
        }
    }

    private fun StringBuilder.writeObject(map: Map<String, Any?>) {
        append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) append(',')
            first = false
            writeString(k)
            append(':')
            writeValue(v)
        }
        append('}')
    }

    private fun StringBuilder.writeArray(list: List<*>) {
        append('[')
        var first = true
        for (v in list) {
            if (!first) append(',')
            first = false
            writeValue(v)
        }
        append(']')
    }

    private fun StringBuilder.writeString(s: String) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001f' -> append("\\u").append("%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    // ============================================================================
    // 反序列化（手写递归下降，足够解析 NCM 返回）
    // ============================================================================

    private class Parser(private val src: String) {
        private var i: Int = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= src.length) return null
            return when (val c = src[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseTrue()
                'f' -> parseFalse()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> error("unexpected char '$c' at $i")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWs()
            val map = LinkedHashMap<String, Any?>()
            if (peek() == '}') { i++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                skipWs()
                val v = parseValue()
                map[key] = v
                skipWs()
                when (peek()) {
                    ',' -> { i++; continue }
                    '}' -> { i++; return map }
                    else -> error("expected , or } at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWs()
            val list = ArrayList<Any?>()
            if (peek() == ']') { i++; return list }
            while (true) {
                skipWs()
                list += parseValue()
                skipWs()
                when (peek()) {
                    ',' -> { i++; continue }
                    ']' -> { i++; return list }
                    else -> error("expected , or ] at $i")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (i < src.length) {
                val c = src[i++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    when (val n = src[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(i, i + 4)
                            i += 4
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        }
                        else -> sb.append(n)
                    }
                } else sb.append(c)
            }
            error("unterminated string")
        }

        private fun parseNumber(): Number {
            val start = i
            if (src[i] == '-') i++
            while (i < src.length && src[i] in '0'..'9') i++
            var isDouble = false
            if (i < src.length && src[i] == '.') { isDouble = true; i++ }
            while (i < src.length && src[i] in '0'..'9') i++
            if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                isDouble = true; i++
                if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                while (i < src.length && src[i] in '0'..'9') i++
            }
            val s = src.substring(start, i)
            return if (isDouble) s.toDouble()
            else s.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
                ?: s.toDouble()
        }

        private fun parseTrue(): Boolean { expect("true"); return true }
        private fun parseFalse(): Boolean { expect("false"); return false }
        private fun parseNull(): Any? { expect("null"); return null }

        private fun expect(c: Char) {
            if (i < src.length && src[i] == c) i++
            else error("expected '$c' at $i, got '${if (i < src.length) src[i] else "<eof>"}'")
        }
        private fun expect(s: String) {
            val sub = if (i + s.length <= src.length) src.substring(i, i + s.length) else ""
            if (sub == s) i += s.length
            else error("expected '$s' at $i")
        }
        private fun peek(): Char = if (i < src.length) src[i] else '\u0000'
        private fun skipWs() {
            while (i < src.length && src[i].isWhitespace()) i++
        }
        private fun error(msg: String): Nothing = error("$msg (near ${src.substring(i.coerceAtMost(src.length - 1), (i + 30).coerceAtMost(src.length))})")
    }
}

// =============================================================================
// 便捷扩展
// =============================================================================

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.ncmString(k: String, default: String = ""): String =
    when (val v = this[k]) {
        is String -> v
        null -> default
        else -> v.toString()
    }

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.ncmInt(k: String, default: Int = 0): Int =
    when (val v = this[k]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        null -> default
        else -> default
    }

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.ncmLong(k: String, default: Long = 0L): Long =
    when (val v = this[k]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: default
        null -> default
        else -> default
    }

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.ncmBool(k: String, default: Boolean = false): Boolean =
    when (val v = this[k]) {
        is Boolean -> v
        is Number -> v.toInt() == 1
        is String -> v.toBoolean()
        null -> default
        else -> default
    }

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.ncmObj(k: String): Map<String, Any?> =
    this[k] as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.ncmList(k: String): List<Any?> =
    this[k] as? List<Any?> ?: emptyList()
