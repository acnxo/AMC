package io.github.acnxo.amc.data.todo

import org.json.JSONObject

/**
 * 待办任务状态，取值对齐服务端 `task_status`。
 *
 * 服务端历史数据里可能还有 3、4，查询时按「进行中」处理。
 */
enum class TodoStatus(val code: Int, val label: String) {
    PENDING(0, "待开始"),
    REDO(1, "重新开始"),
    DOING(2, "进行中"),
    DONE(5, "已完成"),
    ;

    companion object {
        fun of(code: Int): TodoStatus =
            entries.firstOrNull { it.code == code } ?: DOING
    }
}

/**
 * 一条待办事项。
 *
 * 注意字段来源差异：创建接口返回 `title`，查询/修改/状态接口返回 `task_content`，
 * 解析时优先 `task_content`，回退 `title`。
 */
data class TodoItem(
    val taskNo: Long,
    val title: String,
    val content: String?,
    val status: Int,
    val percentCompletion: Int,
    val flagFinish: Boolean,
    val gmtDeadline: String?,
    val gmtDone: String?,
    val gmtCreate: String?,
    val gmtUpdate: String?,
    val minuteEstimate: Int?,
) {
    val isDone: Boolean get() = status == TodoStatus.DONE.code || flagFinish

    companion object {
        fun fromJson(json: JSONObject): TodoItem = TodoItem(
            taskNo = json.optLong("task_no"),
            title = json.optString("task_content").ifBlank { json.optString("title") },
            content = json.optStringOrNull("content"),
            status = json.optString("task_status").toIntOrNull() ?: 0,
            percentCompletion = json.optInt("percent_completion", 0),
            flagFinish = json.optString("flag_finish") == "1",
            gmtDeadline = json.optStringOrNull("gmt_deadline"),
            gmtDone = json.optStringOrNull("gmt_done"),
            gmtCreate = json.optStringOrNull("gmt_create"),
            gmtUpdate = json.optStringOrNull("gmt_update"),
            minuteEstimate = json.optIntOrNull("minute_estimate"),
        )
    }
}

/** 待办分页结果。 */
data class TodoPage(
    val items: List<TodoItem>,
    val pn: Int,
    val ps: Int,
    val total: Int,
)

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)
