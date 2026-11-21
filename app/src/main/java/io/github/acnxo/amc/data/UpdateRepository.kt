package io.github.acnxo.amc.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Releases 读取可安装版本，并下载对应资产。
 *
 * 仓库为公开仓库，匿名访问 GitHub REST API 即可（未认证时每小时 60 次），
 * 因此不需要在 App 内置任何 Token。
 */
class UpdateRepository(
    private val owner: String = DEFAULT_OWNER,
    private val repo: String = DEFAULT_REPO,
) {

    /** 拉取全部 release，按发布时间从新到旧排列。 */
    suspend fun fetchReleases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases?per_page=100")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                throw IllegalStateException(
                    "GitHub API 返回 $code" + if (message.isBlank()) "" else "：${message.take(200)}",
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseReleases(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 下载资产到 [target]，并通过 [onProgress] 回报已下载字节与总字节。
     *
     * 回调发生在 IO 线程，调用方若更新界面状态需自行切回主线程
     * （例如写入 `MutableStateFlow`）。
     */
    suspend fun download(
        asset: ReleaseAsset,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("下载失败，HTTP $code")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            var downloaded = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReleases(body: String): List<ReleaseInfo> {
        val array = JSONArray(body)
        val releases = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val tag = item.optString("tag_name").takeIf { it.isNotBlank() } ?: continue
                val channel = ReleaseInfo.channelOf(tag) ?: continue

                val assetsJson = item.optJSONArray("assets") ?: JSONArray()
                val assets = buildList {
                    for (j in 0 until assetsJson.length()) {
                        val asset = assetsJson.optJSONObject(j) ?: continue
                        val name = asset.optString("name").takeIf { it.isNotBlank() } ?: continue
                        val url = asset.optString("browser_download_url")
                            .takeIf { it.isNotBlank() } ?: continue
                        add(
                            ReleaseAsset(
                                name = name,
                                size = asset.optLong("size"),
                                downloadUrl = url,
                            ),
                        )
                    }
                }
                if (assets.isEmpty()) continue

                add(
                    ReleaseInfo(
                        tag = tag,
                        title = item.optString("name").ifBlank { tag },
                        channel = channel,
                        versionName = tag.removePrefix("v"),
                        publishedAt = item.optString("published_at"),
                        notes = item.optString("body"),
                        assets = assets,
                    ),
                )
            }
        }
        // ISO-8601 字符串可直接按字典序比较
        return releases.sortedByDescending { it.publishedAt }
    }

    companion object {
        const val DEFAULT_OWNER = "acnxo"
        const val DEFAULT_REPO = "AMC"

        private const val USER_AGENT = "AMC-Android"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
    }
}
