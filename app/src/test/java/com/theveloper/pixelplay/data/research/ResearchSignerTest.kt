package com.theveloper.pixelplay.data.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Base64
import java.util.UUID

/**
 * 签名实现测试。
 *
 * 服务端联调向量（signKey / licenseKey / 期望签名等）属于敏感凭据字面量，
 * **不入仓库**：通过环境变量注入后运行 [serverVectorTests]；未设置时自动跳过。
 *
 * 环境变量（由服务端联调脚本 / 本机私有配置提供）：
 *   RESEARCH_TEST_SIGN_KEY          signKey 原串
 *   RESEARCH_TEST_LICENSE_KEY       活动密钥
 *   RESEARCH_TEST_TIMESTAMP         秒级时间戳
 *   RESEARCH_TEST_NONCE             一次性 nonce
 *   RESEARCH_TEST_LISTEN_BODY       POST /activity/v1/listen 的完整测试 body
 *   RESEARCH_TEST_EXPECTED_LISTEN   期望的 listen 签名（Base64）
 *   RESEARCH_TEST_EXPECTED_ME       期望的 me（空体）签名（Base64）
 *
 * 关键口径（与《03 数据格式与接口规范》3.1 一致）：
 * - HMAC 密钥 = signKey **原串的 UTF-8 字节**（服务端 crypto.createHmac('sha256', signKey)），
 *   不做 Base64 解码
 * - canonical 第二行是带 /activity/v1 前缀的全路径（含 query，对应服务端 req.originalUrl）
 * - body 哈希 = 实际发送字节的 SHA-256 小写 hex；GET 为空体哈希（e3b0c442…）
 */
class ResearchSignerTest {

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    /** 随机生成密钥与请求参数，做实现自洽性验证（无任何凭据字面量，始终运行） */
    @Test
    fun `signature is self consistent and uses raw utf8 key`() {
        val signKey = UUID.randomUUID().toString() + "="   // 形似 Base64 但按原串使用
        val licenseKey = "XS-TEST-XXXX-XXXX"
        val ts = 1795797221L
        val nonce = UUID.randomUUID().toString()
        val body = "test-body-bytes".toByteArray(Charsets.UTF_8)

        val signed = ResearchSigner.sign(
            method = "POST",
            pathWithQuery = "/activity/v1/listen",
            rawBody = body,
            licenseKey = licenseKey,
            signKeyId = "sk_test",
            signKeyRaw = signKey,
            appVersion = "test",
            serverTimeOffsetSec = 0
        )
        assertEquals(ts.toString(), signed.timestamp.toString())
        assertEquals(nonce, signed.nonce)

        // 独立重建 canonical 并手算 HMAC，验证 sign() 的拼接与密钥用法
        val canonical = listOf(
            "POST",
            "/activity/v1/listen",
            ResearchSigner.sha256Hex(body),
            licenseKey,
            ts.toString(),
            nonce
        ).joinToString("\n")
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(signKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
        assertEquals(expected, signed.signature)

        // 反向断言：若实现误按 Base64 解码密钥，结果必然不同
        val wrongKey = Base64.getDecoder().decode(signKey)
        mac.init(javax.crypto.spec.SecretKeySpec(wrongKey, "HmacSHA256"))
        val wrong = Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
        assertNotEquals(wrong, signed.signature)
    }

    /** 服务端联调向量（需环境变量注入，见类注释；未设置时跳过） */
    @Test
    fun `server vectors match when provided`() {
        val signKey = env("RESEARCH_TEST_SIGN_KEY")
        assumeTrue("服务端向量未配置（RESEARCH_TEST_* 环境变量），跳过", signKey != null)
        val licenseKey = env("RESEARCH_TEST_LICENSE_KEY")!!
        val ts = env("RESEARCH_TEST_TIMESTAMP")!!
        val nonce = env("RESEARCH_TEST_NONCE")!!
        val listenBody = env("RESEARCH_TEST_LISTEN_BODY")!!
        val expectedListen = env("RESEARCH_TEST_EXPECTED_LISTEN")!!
        val expectedMe = env("RESEARCH_TEST_EXPECTED_ME")!!

        val emptyBodyHash = ResearchSigner.sha256Hex(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyBodyHash)

        assertEquals(
            expectedListen,
            ResearchSigner.computeSignature(
                signKeyRaw = signKey,
                method = "POST",
                pathWithQuery = "/activity/v1/listen",
                bodyHashHex = ResearchSigner.sha256Hex(listenBody.toByteArray(Charsets.UTF_8)),
                licenseKey = licenseKey,
                timestampSec = ts.toLong(),
                nonce = nonce
            )
        )
        assertEquals(
            expectedMe,
            ResearchSigner.computeSignature(
                signKeyRaw = signKey,
                method = "GET",
                pathWithQuery = "/activity/v1/me",
                bodyHashHex = emptyBodyHash,
                licenseKey = licenseKey,
                timestampSec = ts.toLong(),
                nonce = nonce
            )
        )
    }
}
