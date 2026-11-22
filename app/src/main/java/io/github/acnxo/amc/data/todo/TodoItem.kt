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
 * 内容类型，取值对齐服务端 `content_type`（见 ToDo OpenAPI README 的枚举表）。
 */
enum class TodoContentType(val code: String, val label: String) {
    TXT("1", "txt"),
    HTML("2", "html"),
    MARKDOWN("3", "markdown"),
    IMAGE("4", "img"),
    BASE64("6", "base64"),
    ENCRYPTED("7", "加密"),
    ;

    companion object {
        /** 未知取值直接回显原值，避免丢信息。 */
        fun labelOf(code: String?): String =
            entries.firstOrNull { it.code == code }?.label ?: (code ?: "未知")
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
    /** 实际耗时（分钟），服务端恒有值，未填报时为 0。 */
    val minuteActual: Int,
    /** 内容类型：`1` txt、`2` html、`3` markdown 等，见 [TodoContentType]。 */
    val contentType: String?,
    /** 列表接口返回的是派生优先级 (100-重要)*(100-紧急)，详情接口是数据库原始值。 */
    val taskPriority: Int,
    val levelImportance: Int?,
    val levelUrgency: Int?,
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
            minuteActual = json.optInt("minute_actual", 0),
            contentType = json.optStringOrNull("content_type"),
            taskPriority = json.optInt("task_priority", 0),
            levelImportance = json.optIntOrNull("level_importance"),
            levelUrgency = json.optIntOrNull("level_urgency"),
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
