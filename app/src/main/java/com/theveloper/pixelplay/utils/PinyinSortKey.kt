package com.theveloper.pixelplay.utils

import java.text.Collator
import java.util.Locale

/**
 * 名称的拼音排序键 / 比较器。
 *
 * Android 的 ICU `Collator` 对中文语言环境（`Locale.CHINA`）默认使用**拼音**排序，
 * `getCollationKey` 返回的字节串按字节序比较与 `Collator.compare` 完全等价
 * （PRIMARY 强度 = 忽略大小写与声调差异）。因此：
 *  - 内存排序直接用 [comparator]；
 *  - SQL 排序把键存进 `songs` 表的 BLOB 列（`title_sort_key` 等），
 *    `ORDER BY key` 的字节序比较即可得到与内存一致的拼音顺序，无需第三方拼音库。
 */
object PinyinSortKey {

    private val collatorLocal: ThreadLocal<Collator> = ThreadLocal.withInitial {
        Collator.getInstance(Locale.CHINA).apply {
            // PRIMARY：忽略大小写与声调（"a" == "A" == "á"），名称排序符合直觉
            strength = Collator.PRIMARY
        }
    }

    /** 生成排序键（空/ null 输入返回空字节数组，保证列值非 NULL 且可排序）。 */
    fun of(text: String?): ByteArray =
        collatorLocal.get().getCollationKey(text.orEmpty()).toByteArray()

    /** 内存字符串比较器（与 SQL 的 BLOB 字节序语义一致）。 */
    val comparator: Comparator<String> = Comparator { a, b ->
        collatorLocal.get().compare(a.orEmpty(), b.orEmpty())
    }

    /** 生成按名称升序的比较器。 */
    fun <T> ascending(selector: (T) -> String?): Comparator<T> =
        Comparator { a, b -> comparator.compare(selector(a), selector(b)) }
}
