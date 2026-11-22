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

    /** 分页查询；[filter] 0 待开始、2 进行中、3 已超时、5 已完成、100 全部。 */
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

    /**
     * PATCH 语义：为 null 或不传的字段一律不修改。
     *
     * - [deadline] 非空时设置完成截止时间；
     * - [clearDeadline] 为 true 时传 `flag_clear_deadline`，服务端把截止时间置 NULL；
     * - [percentCompletion] 会联动任务状态与完成时间：
     *   `0` 回到待开始并清空完成时间，`100` 标记已完成并写入完成时间。
     */
    suspend fun modify(
        taskNo: Long,
        title: String? = null,
        content: String? = null,
        deadline: java.util.Date? = null,
        clearDeadline: Boolean = false,
        minuteEstimate: Int? = null,
        levelImportance: Int? = null,
        levelUrgency: Int? = null,
        percentCompletion: Int? = null,
    ): TodoItem = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("task_no", taskNo)
            title?.let { put("title", it) }
            content?.let { put("content", it) }
            deadline?.let { put("gmt_deadline", TodoTime.formatUtc(it)) }
            if (clearDeadline) put("flag_clear_deadline", true)
            minuteEstimate?.let { put("minute_estimate", it) }
            levelImportance?.let { put("level_importance", it) }
            levelUrgency?.let { put("level_urgency", it) }
            percentCompletion?.let { put("percent_completion", it) }
        }
        TodoItem.fromJson(execute(jsonRequest("PATCH", "Modify", body)))
    }

    /**
     * 逻辑删除：`flag_delete=1`，数据保留可恢复。
     *
     * 服务端语义：`flag_delete` 优先级高于其它编辑字段，同一次请求里带上它时，
     * 其余字段会被忽略；重复删除幂等。
     */
    suspend fun softDelete(taskNo: Long): TodoItem = setDeleteFlag(taskNo, deleted = true)

    /** 恢复被逻辑删除的任务：`flag_delete=0`（已删除的任务必须先恢复才能普通编辑）。 */
    suspend fun restore(taskNo: Long): TodoItem = setDeleteFlag(taskNo, deleted = false)

    private suspend fun setDeleteFlag(taskNo: Long, deleted: Boolean): TodoItem =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("task_no", taskNo)
                put("flag_delete", if (deleted) "1" else "0")
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
        const val FILTER_PENDING = 0
        const val FILTER_DOING = 2

        /** 已超时：截止时间已过。服务端另有 `4`（已逾期），语义待确认前先不用。 */
        const val FILTER_OVERDUE = 3
        const val FILTER_DONE = 5
        const val FILTER_ALL = 100

        private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()
    }
}
