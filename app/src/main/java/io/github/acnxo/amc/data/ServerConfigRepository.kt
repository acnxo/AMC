package io.github.acnxo.amc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 服务器节点配置的本地持久化。
 *
 * 使用 SharedPreferences 保存，并通过 [config] 暴露可订阅的当前值。
 * 后续接入 API 时，可直接：
 *
 * ```kotlin
 * val baseUrl = ServerConfigRepository.getInstance(context).current().baseUrl
 * ```
 *
 * 或订阅 `config` 变化，在地址切换后重建 API 客户端。
 */
class ServerConfigRepository private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_MODE, config.mode.name)
            .putString(KEY_DOMAIN, config.domain)
            .putBoolean(KEY_HTTPS, config.useHttps)
            .putString(KEY_IP, config.ip)
            .putInt(KEY_PORT, config.port)
            .apply()
        _config.value = config
    }

    fun current(): ServerConfig = _config.value

    private fun readConfig(): ServerConfig = ServerConfig(
        mode = prefs.getString(KEY_MODE, null)
            ?.let { runCatching { ServerMode.valueOf(it) }.getOrNull() }
            ?: ServerMode.DOMAIN,
        domain = prefs.getString(KEY_DOMAIN, ServerConfig.DEFAULT_DOMAIN)
            ?: ServerConfig.DEFAULT_DOMAIN,
        useHttps = prefs.getBoolean(KEY_HTTPS, true),
        ip = prefs.getString(KEY_IP, "") ?: "",
        port = prefs.getInt(KEY_PORT, ServerConfig.DEFAULT_PORT),
    )

    companion object {
        private const val PREFS_NAME = "amc_server_config"
        private const val KEY_MODE = "mode"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_HTTPS = "use_https"
        private const val KEY_IP = "ip"
        private const val KEY_PORT = "port"

        @Volatile
        private var instance: ServerConfigRepository? = null

        fun getInstance(context: Context): ServerConfigRepository =
            instance ?: synchronized(this) {
                instance ?: ServerConfigRepository(context.applicationContext).also { instance = it }
            }
    }
}
