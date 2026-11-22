package io.github.acnxo.amc.data.openapi

import io.github.acnxo.amc.data.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 单个探测项的测试结果。 */
data class ProbeResult(
    val title: String,
    val path: String,
    val success: Boolean,
    /** 一行摘要：成功时为关键信息 + 耗时，失败时为原因。 */
    val summary: String,
)

/** 节点测试报告：存活探测 + 鉴权握手。 */
data class NodeProbeReport(
    val live: ProbeResult,
    val handshake: ProbeResult,
) {
    /** 两项都通过才表示服务节点达到可用的最低要求。 */
    val allSuccess: Boolean get() = live.success && handshake.success
}

/**
 * 服务节点连通性探测。
 *
 * 用当前生效配置依次请求两个端点：
 * 1. `GET /API/V1/Health/Live` —— 服务是否存活（无需鉴权）；
 * 2. `GET /OpenAPI/V1/Meta/Region/AZ/Server/Hello` —— AK/SK 签名能否通过握手校验。
 *
 * 握手接口豁免授权范围校验，因此它能独立回答「签名配置是否正确」，
 * 不受业务接口 scope 授权的影响。
 */
object NodeProbe {

    const val TITLE_LIVE = "服务存活"
    const val TITLE_HANDSHAKE = "鉴权握手"

    const val PATH_LIVE = "/API/V1/Health/Live"
    const val PATH_HANDSHAKE = "/OpenAPI/V1/Meta/Region/AZ/Server/Hello"

    /** 握手成功的判定标识（文档要求不能只看 HTTP 200）。 */
    private const val HANDSHAKE_OK = "ServerHello"

    /** 信封里不存在 code 字段时的占位值。 */
    private const val NO_CODE = Int.MIN_VALUE

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 依次执行两项探测，不会抛异常，失败信息放在结果里。 */
    suspend fun run(config: ServerConfig): NodeProbeReport = withContext(Dispatchers.IO) {
        NodeProbeReport(
            live = probeLive(config.BASE_URL),
            handshake = probeHandshake(config),
        )
    }

    // ---------------------------------------------------------------
    // 服务存活
    // ---------------------------------------------------------------

    private fun probeLive(baseUrl: String): ProbeResult {
        val start = System.nanoTime()
        return runCatching {
            val request = Request.Builder().url(baseUrl + PATH_LIVE).get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val cost = costMs(start)
                if (!response.isSuccessful) {
                    return failLive("HTTP ${response.code} · $cost ms" + bodyHint(text))
                }
                // 返回 JSON 信封时再校验 code；纯文本/其它健康检查格式只看 HTTP 状态。
                val json = runCatching { JSONObject(text) }.getOrNull()
                val code = json?.optInt("code", NO_CODE) ?: NO_CODE
                if (code != NO_CODE && code != OpenApiError.CODE_OK) {
                    return failLive(
                        OpenApiError.describe(
                            code,
                            json?.optString("message").orEmpty(),
                            response.code,
                        ),
                    )
                }
                ProbeResult(TITLE_LIVE, PATH_LIVE, true, "HTTP ${response.code} · $cost ms")
            }
        }.getOrElse { error ->
            failLive(error.message ?: "请求失败")
        }
    }

    // ---------------------------------------------------------------
    // 鉴权握手
    // ---------------------------------------------------------------

    private fun probeHandshake(config: ServerConfig): ProbeResult {
        if (!config.hasCredentials) {
            return failHandshake("未配置 AK/SK，无法完成鉴权握手")
        }
        val start = System.nanoTime()
        return runCatching {
            val builder = Request.Builder().url(config.BASE_URL + PATH_HANDSHAKE).get()
            OpenApiSigner.signHeaders(
                accessKey = config.accessKey,
                secretKey = config.secretKey,
                method = "GET",
                path = PATH_HANDSHAKE,
                rawQuery = null,
                body = null,
            ).forEach { (name, value) -> builder.header(name, value) }

            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val cost = costMs(start)
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: return failHandshake("响应不是合法 JSON（HTTP ${response.code}）")
                val code = json.optInt("code", -1)
                if (code != OpenApiError.CODE_OK) {
                    return failHandshake(
                        OpenApiError.describe(
                            code,
                            json?.optString("message").orEmpty(),
                            response.code,
                        ),
                    )
                }
                val data = json.optJSONObject("data")
                val handshake = data?.optString("handshake").orEmpty()
                if (handshake != HANDSHAKE_OK) {
                    return failHandshake("握手标识异常：${handshake.ifBlank { "缺失" }}")
                }
                ProbeResult(TITLE_HANDSHAKE, PATH_HANDSHAKE, true, describeHello(data, cost))
            }
        }.getOrElse { error ->
            failHandshake(error.message ?: "请求失败")
        }
    }

    /** 拼接握手成功后的关键信息：服务名/版本、凭据号、授权条数、耗时。 */
    private fun describeHello(data: JSONObject?, cost: Long): String {
        val server = data?.optJSONObject("server")
        val clientInfo = data?.optJSONObject("client")
        val name = server?.optString("name").orEmpty()
        val version = server?.optString("version").orEmpty()
        val credentialNo = clientInfo?.optString("credential_no").orEmpty()
        val scopeCount = clientInfo?.optInt("scope_count", -1) ?: -1
        return buildList {
            if (name.isNotBlank() || version.isNotBlank()) add("$name $version".trim())
            if (credentialNo.isNotBlank()) add("凭据 $credentialNo")
            if (scopeCount >= 0) {
                add(if (scopeCount == 0) "授权不限" else "授权 $scopeCount 条")
            }
            add("$cost ms")
        }.joinToString(" · ")
    }

    private fun failLive(summary: String) = ProbeResult(TITLE_LIVE, PATH_LIVE, false, summary)

    private fun failHandshake(summary: String) =
        ProbeResult(TITLE_HANDSHAKE, PATH_HANDSHAKE, false, summary)

    private fun costMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun bodyHint(text: String): String =
        if (text.isBlank()) "" else "（${text.take(100)}）"
}
