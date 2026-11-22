package io.github.acnxo.amc.data.todo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
 */
class TodoApi(baseUrl: String) {

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
        val url = "$base/OpenAPI/V1/Meta/CMS/ToDo/OffsetPage" +
            "?pn=$pn&ps=$ps&filter=$filter&ordMode=$ordMode"
        val data = execute(Request.Builder().url(url).get().build())
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
        val url = "$base/OpenAPI/V1/Meta/CMS/ToDo/Detail?id=$taskNo"
        TodoItem.fromJson(execute(Request.Builder().url(url).get().build()))
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
        val data = execute(post("Create", body))
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
        TodoItem.fromJson(execute(patch("Modify", body)))
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
            TodoItem.fromJson(execute(patch("StatusToggle", body)))
        }

    private fun post(action: String, body: JSONObject): Request =
        Request.Builder()
            .url("$base/OpenAPI/V1/Meta/CMS/ToDo/$action")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private fun patch(action: String, body: JSONObject): Request =
        Request.Builder()
            .url("$base/OpenAPI/V1/Meta/CMS/ToDo/$action")
            .patch(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

    /** 执行请求并解包信封，`code != 1` 抛 [TodoApiException]。 */
    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw TodoApiException(
                    "请求失败：HTTP ${response.code}" + if (text.isBlank()) "" else "（${text.take(120)}）",
                )
            }
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw TodoApiException("响应不是合法 JSON")
            }
            val code = json.optInt("code", -1)
            if (code != CODE_OK) {
                throw TodoApiException(
                    json.optString("message").ifBlank { "业务错误（code=$code）" },
                    code,
                )
            }
            return json.optJSONObject("data") ?: JSONObject()
        }
    }

    companion object {
        private const val CODE_OK = 1
        const val FILTER_ALL = 100
        const val FILTER_DONE = 5

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
