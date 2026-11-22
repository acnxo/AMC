package io.github.acnxo.amc.data.openapi

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OpenAPI AK/SK 签名工具（META1-HMAC-SHA256）。
 *
 * 对应平台文档《OpenAPI AK/SK 接口签名指南》v1.0，用于 CMS 待办等开放接口的鉴权。
 * 无状态、线程安全。
 *
 * 待签串为 6 段，段间只放一个 LF（0x0A），末尾不加换行：
 * ```
 * METHOD\nPATH\nSORTED_QUERY\nBODY_SHA256\nTIMESTAMP\nNONCE
 * ```
 *
 * 使用方式：
 * ```kotlin
 * val headers = OpenApiSigner.signHeaders(ak, sk, "GET", path, rawQuery, body)
 * headers.forEach { (name, value) -> builder.header(name, value) }
 * ```
 */
object OpenApiSigner {

    /** 签名请求头 */
    const val HEADER_AUTHORIZATION = "Authorization"

    /** 时间戳请求头（Unix 秒） */
    const val HEADER_TIMESTAMP = "X-META-TS"

    /** 随机串请求头 */
    const val HEADER_NONCE = "X-META-NONCE"

    private const val SCHEME_PREFIX = "META1-"
    private const val ALGORITHM = "HMAC-SHA256"

    /** 空请求体的固定 SHA-256（大写十六进制） */
    private const val EMPTY_BODY_SHA256 =
        "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"

    /** nonce 规范：8~64 位，只允许大写字母、数字、下划线、连字符 */
    private val NONCE_PATTERN = Regex("^[A-Z0-9_-]{8,64}$")

    private val HEX_UPPER = "0123456789ABCDEF".toCharArray()

    private val RANDOM = SecureRandom()

    /**
     * 生成签名请求头，时间戳与 nonce 自动生成。
     *
     * 每次请求（含失败重试）都必须重新调用，避免 nonce 复用返回 19007。
     */
    fun signHeaders(
        accessKey: String,
        secretKey: String,
        method: String,
        path: String,
        rawQuery: String?,
        body: ByteArray?,
    ): Map<String, String> = signHeaders(
        accessKey = accessKey,
        secretKey = secretKey,
        method = method,
        path = path,
        rawQuery = rawQuery,
        body = body,
        timestamp = (System.currentTimeMillis() / 1000L).toString(),
        nonce = newNonce(),
    )

    /** 生成签名请求头；[timestamp] 与 [nonce] 固定时可用于对照文档中的测试向量。 */
    fun signHeaders(
        accessKey: String,
        secretKey: String,
        method: String,
        path: String,
        rawQuery: String?,
        body: ByteArray?,
        timestamp: String,
        nonce: String,
    ): Map<String, String> {
        require(isValidNonce(nonce)) {
            "nonce 不符合规范：应为 8~64 位大写字母/数字/下划线/连字符，实际=$nonce"
        }
        val canonical = buildCanonical(method, path, rawQuery, body, timestamp, nonce)
        val signature = sign(secretKey, canonical)
        return linkedMapOf(
            HEADER_AUTHORIZATION to
                "$SCHEME_PREFIX$ALGORITHM Credential=$accessKey, Signature=$signature",
            HEADER_TIMESTAMP to timestamp,
            HEADER_NONCE to nonce,
        )
    }

    /** 构造 6 段待签串。 */
    fun buildCanonical(
        method: String,
        path: String,
        rawQuery: String?,
        body: ByteArray?,
        timestamp: String,
        nonce: String,
    ): String {
        require(method.isNotEmpty()) { "method 不能为空" }
        require(path.isNotEmpty()) { "path 不能为空" }
        require(timestamp.isNotEmpty()) { "timestamp 不能为空" }
        require(isValidNonce(nonce)) { "nonce 不符合规范" }
        return listOf(
            method.uppercase(),
            path,
            sortedQuery(rawQuery),
            sha256HexUpper(body),
            timestamp,
            nonce,
        ).joinToString("\n")
    }

    /**
     * 规范化查询串：键与值先百分号解码、再按 RFC 3986 编码，
     * 然后按键升序、同键按值升序排列，用 `&` 连接。
     */
    fun sortedQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) return ""
        val pairs = rawQuery.split('&')
            .filter { it.isNotEmpty() }
            .map { piece ->
                val index = piece.indexOf('=')
                if (index < 0) {
                    normalize(piece) to ""
                } else {
                    normalize(piece.substring(0, index)) to normalize(piece.substring(index + 1))
                }
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
        return pairs.joinToString("&") { (key, value) -> "$key=$value" }
    }

    /** 请求体摘要：SHA-256 大写十六进制；null 视为空体。 */
    fun sha256HexUpper(body: ByteArray?): String {
        if (body == null || body.isEmpty()) return EMPTY_BODY_SHA256
        val digest = MessageDigest.getInstance("SHA-256").digest(body)
        val out = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            out.append(HEX_UPPER[value shr 4]).append(HEX_UPPER[value and 0x0F])
        }
        return out.toString()
    }

    /** 签名：Base64 标准编码的 HMAC-SHA256(SK, 待签串)。 */
    fun sign(secretKey: String, canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    /** 生成 nonce：16 字节随机数转 32 位大写十六进制。 */
    fun newNonce(): String {
        val bytes = ByteArray(16)
        RANDOM.nextBytes(bytes)
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(HEX_UPPER[value shr 4]).append(HEX_UPPER[value and 0x0F])
            }
        }
    }

    /** nonce 是否符合规范（8~64 位大写字母/数字/下划线/连字符）。 */
    fun isValidNonce(nonce: String?): Boolean =
        nonce != null && NONCE_PATTERN.matches(nonce)

    // ---------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------

    /** 先解码再编码，保证规范化幂等。 */
    private fun normalize(value: String): String {
        if (value.isEmpty()) return ""
        return percentEncode(percentDecode(value))
    }

    /** RFC 3986 编码：只保留 A-Z a-z 0-9 - . _ ~，空格为 %20，'+' 为 %2B。 */
    private fun percentEncode(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val out = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val v = byte.toInt() and 0xFF
            val unreserved = (v in 'A'.code..'Z'.code) ||
                (v in 'a'.code..'z'.code) ||
                (v in '0'.code..'9'.code) ||
                v == '-'.code || v == '.'.code || v == '_'.code || v == '~'.code
            if (unreserved) {
                out.append(v.toChar())
            } else {
                out.append('%').append(HEX_UPPER[v shr 4]).append(HEX_UPPER[v and 0x0F])
            }
        }
        return out.toString()
    }

    /** 只解 %XX，'+' 保持原样。 */
    private fun percentDecode(value: String): String {
        val out = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '%' && index + 2 < value.length) {
                val high = Character.digit(value[index + 1], 16)
                val low = Character.digit(value[index + 2], 16)
                if (high >= 0 && low >= 0) {
                    out.write((high shl 4) + low)
                    index += 3
                    continue
                }
            }
            val bytes = current.toString().toByteArray(StandardCharsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            index++
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }
}
