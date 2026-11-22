package io.github.acnxo.amc.data.todo

import io.github.acnxo.amc.data.openapi.OpenApiError
import io.github.acnxo.amc.data.openapi.OpenApiSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** 业务失败：服务端返回 `code != 1`，或 HTTP 非 2xx。 */
class TodoApiException(
    message: String,
    val code: Int? = null,
) : Exception(message)

/**
 * CMS ToDo OpenAPI 客户端。
 *
 * 端点均相对 `BASE_URL`（来自「内容服务节点」当前生效配置）：
 * - `POST   /OpenAPI/V1/Meta/CMS/ToDo/Create`
 * - `GET    /OpenAPI/V1/Meta/CMS/ToDo/OffsetPage`
 * - `GET    /OpenAPI/V1/Meta/CMS/ToDo/Detail`
 * - `PATCH  /OpenAPI/V1/Meta/CMS/ToDo/Modify`
 * - `PATCH  /OpenAPI/V1/Meta/CMS/ToDo/StatusToggle`
 *
 * 服务端业务成败以响应体 `code` 为准（HTTP 200 也可能是业务失败）。
 *
 * 若当前节点配置了 AK/SK，每个请求都会按 META1-HMAC-SHA256 附带签名头
 * （`Authorization` / `X-META-TS` / `X-META-NONCE`）；未配置时不加签名头，
 * 兼容尚未开启鉴权的环境。
 */
class TodoApi(
    baseUrl: String,
    private val accessKey: String = "",
    private val secretKey: String = "",
) {

    private val base = baseUrl.trim().trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 分页查询；[filter] 100 表示全部，5 表示已完成。 */
    suspend fun page(
        pn: Int = 1,
        ps: Int = 100,
        filter: Int = FILTER_ALL,
        ordMode: Int = 2,
    ): TodoPage = withContext(Dispatchers.IO) {
        val query = "pn=$pn&ps=$ps&filter=$filter&ordMode=$ordMode"
        val data = execute(
            request(method = "GET", path = pathOf("OffsetPage"), query = query, bodyText = null),
        )
        val list = data.optJSONArray("list") ?: JSONArray()
        TodoPage(
            items = buildList {
                for (i in 0 until list.length()) {
                    list.optJSONObject(i)?.let { add(TodoItem.fromJson(it)) }
                }
            },
            pn = data.optInt("pn", pn),
            ps = data.optInt("ps", ps),
            total = data.optInt("total", 0),
        )
    }

    suspend fun detail(taskNo: Long): TodoItem = withContext(Dispatchers.IO) {
        val data = execute(
            request(method = "GET", path = pathOf("Detail"), query = "id=$taskNo", bodyText = null),
        )
        TodoItem.fromJson(data)
    }

    suspend fun create(
        title: String,
        content: String,
        deadline: java.util.Date? = null,
        minuteEstimate: Int? = null,
        levelImportance: Int? = null,
        levelUrgency: Int? = null,
    ): TodoItem = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("title", title)
            put("content", content)
            deadline?.let { put("gmt_deadline", TodoTime.formatUtc(it)) }
            minuteEstimate?.let { put("minute_estimate", it) }
            levelImportance?.let { put("level_importance", it) }
            levelUrgency?.let { put("level_urgency", it) }
        }
        val data = execute(jsonRequest("POST", "Create", body))
        TodoItem.fromJson(data)
    }

    /** PATCH 语义：传 null 的字段不修改。 */
    suspend fun modify(
        taskNo: Long,
        title: String? = null,
        content: String? = null,
        deadline: java.util.Date? = null,
        minuteEstimate: Int? = null,
        percentCompletion: Int? = null,
    ): TodoItem = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("task_no", taskNo)
            title?.let { put("title", it) }
            content?.let { put("content", it) }
            deadline?.let { put("gmt_deadline", TodoTime.formatUtc(it)) }
            minuteEstimate?.let { put("minute_estimate", it) }
            percentCompletion?.let { put("percent_completion", it) }
        }
        TodoItem.fromJson(execute(jsonRequest("PATCH", "Modify", body)))
    }

    /** 快捷完成/取消完成；[quickComplete] 为 true 时服务端置为已完成。 */
    suspend fun quickComplete(taskNo: Long, complete: Boolean): TodoItem =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("task_no", taskNo)
                if (complete) {
                    put("quick_complete", true)
                } else {
                    put("task_status", TodoStatus.PENDING.code.toString())
                }
            }
            TodoItem.fromJson(execute(jsonRequest("PATCH", "StatusToggle", body)))
        }

    private fun pathOf(action: String): String = "/OpenAPI/V1/Meta/CMS/ToDo/$action"

    private fun jsonRequest(method: String, action: String, body: JSONObject): Request =
        request(method = method, path = pathOf(action), query = null, bodyText = body.toString())

    /**
     * 构造请求并按需附加 AK/SK 签名头。
     *
     * 签名必须基于**实际发送的字节**：这里先用同一份 UTF-8 字节算摘要，
     * 再把这份字节交给 OkHttp，避免重新序列化导致摘要不一致（19005）。
     */
    private fun request(
        method: String,
        path: String,
        query: String?,
        bodyText: String?,
    ): Request {
        val url = if (query.isNullOrEmpty()) "$base$path" else "$base$path?$query"
        val body = bodyText?.toByteArray(StandardCharsets.UTF_8)
        val builder = Request.Builder().url(url)
        if (body == null) {
            builder.method(method, null)
        } else {
            builder.method(method, body.toRequestBody(JSON_MEDIA_TYPE))
        }
        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            OpenApiSigner
                .signHeaders(accessKey, secretKey, method, path, query, body)
                .forEach { (name, value) -> builder.header(name, value) }
        }
        return builder.build()
    }

    /** 执行请求并解包信封，`code != 1` 抛 [TodoApiException]。 */
    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw TodoApiException(
                    if (response.isSuccessful) {
                        "响应不是合法 JSON"
                    } else {
                        "请求失败：HTTP ${response.code}" +
                            if (text.isBlank()) "" else "（${text.take(120)}）"
                    },
                )
            val code = json.optInt("code", -1)
            if (code != CODE_OK) {
                throw TodoApiException(
                    OpenApiError.describe(code, json.optString("message"), response.code),
                    code,
                )
            }
            if (!response.isSuccessful) {
                throw TodoApiException("请求失败：HTTP ${response.code}")
            }
            return json.optJSONObject("data") ?: JSONObject()
        }
    }

    companion object {
        private const val CODE_OK = 1
        const val FILTER_ALL = 100
        const val FILTER_DONE = 5

        private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()
    }
}
