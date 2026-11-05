package io.github.acnxo.amc.data

/** 服务器连接模式：域名 / IP + 端口。 */
enum class ServerMode {
    DOMAIN,
    IP_PORT,
}

/**
 * 云端服务基础地址配置。
 *
 * [baseUrl] 是后续 API 调用唯一需要依赖的入口，
 * 例如 Retrofit 直接使用 `baseUrl` 作为 baseUrl 即可。
 */
data class ServerConfig(
    val mode: ServerMode = ServerMode.DOMAIN,
    val domain: String = DEFAULT_DOMAIN,
    val useHttps: Boolean = true,
    val ip: String = "",
    val port: Int = DEFAULT_PORT,
) {

    /** 由当前模式拼接出的基础地址，如 `https://acanx.com`、`http://192.168.1.10:8080`。 */
    val baseUrl: String
        get() = when (mode) {
            ServerMode.DOMAIN -> "${scheme()}://${domain.trim().trimEnd('/')}"
            ServerMode.IP_PORT -> "${scheme()}://${ip.trim()}:$port"
        }

    fun scheme(): String = if (useHttps) "https" else "http"

    /** 返回校验失败原因；配置合法时返回 null。 */
    fun validate(): String? = when (mode) {
        ServerMode.DOMAIN -> {
            val host = domain.trim()
            when {
                host.isEmpty() -> "请输入域名"
                host.length > 253 -> "域名长度不能超过 253 个字符"
                !DOMAIN_REGEX.matches(host) -> "域名格式不正确，例如 acanx.com"
                else -> null
            }
        }

        ServerMode.IP_PORT -> {
            val host = ip.trim()
            when {
                host.isEmpty() -> "请输入 IP 地址"
                !IPV4_REGEX.matches(host) -> "IPv4 地址格式不正确"
                port !in 1..65535 -> "端口需在 1-65535 之间"
                else -> null
            }
        }
    }

    companion object {
        const val DEFAULT_DOMAIN = "acanx.com"
        const val DEFAULT_PORT = 443

        private val DOMAIN_REGEX = Regex(
            "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)" +
                "(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$",
        )

        private val IPV4_REGEX = Regex(
            "^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}" +
                "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$",
        )
    }
}
