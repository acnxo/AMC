package io.github.acnxo.amc.data.todo

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 服务端时间格式固定为 UTC 的 `yyyy-MM-dd'T'HH:mm:ss'Z'`（`Z` 是字面量）。
 *
 * 这里统一做「服务端 UTC 字符串 ↔ 本地 Date」的转换，界面只处理本地时间。
 */
object TodoTime {

    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    private fun utcFormat(): SimpleDateFormat =
        SimpleDateFormat(PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun localFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /** 解析服务端时间；格式不符或为空时返回 null。 */
    fun parseUtc(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return try {
            utcFormat().parse(value)
        } catch (_: ParseException) {
            null
        }
    }

    /** 把本地时间转成服务端要求的 UTC 字符串。 */
    fun formatUtc(date: Date): String = utcFormat().format(date)

    /** 服务端时间 → 本地可读文本，例如 `2026-11-26 15:05`。 */
    fun formatLocal(value: String?): String =
        parseUtc(value)?.let { localFormat().format(it) }.orEmpty()

    /** 毫秒时间戳 → 服务端 UTC 字符串。 */
    fun formatUtc(millis: Long): String = formatUtc(Date(millis))
}
