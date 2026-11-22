package io.github.acnxo.amc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 某一类服务节点（[ServerKind]）的配置集合。
 *
 * [selectedId] 指向当前生效的配置档案；为空表示该类型下还没有配置。
 */
data class ServerNodeState(
    val profiles: List<ServerProfile> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: ServerProfile? get() = profiles.firstOrNull { it.id == selectedId }
}

/**
 * 服务节点配置的本地持久化。
 *
 * 内容服务与 Finv 服务各自维护一组配置档案，每个档案有独立的配置名，
 * 同一时刻各类型各有一份“当前生效”配置。后续接入 API 时可直接：
 *
 * ```kotlin
 * val BASE_URL = ServerConfigRepository.getInstance(context)
 *     .activeConfig(ServerKind.CMS)?.BASE_URL
 * ```
 *
 * 或订阅 [states] 变化，在配置切换后重建 API 客户端。
 */
class ServerConfigRepository private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _states = MutableStateFlow(readAll())
    val states: StateFlow<Map<ServerKind, ServerNodeState>> = _states.asStateFlow()

    fun state(kind: ServerKind): ServerNodeState =
        _states.value[kind] ?: ServerNodeState()

    /** 当前生效的配置档案；未添加配置时返回 null。 */
    fun active(kind: ServerKind): ServerProfile? = state(kind).selected

    /** 当前生效的连接参数；未添加配置时返回 null。 */
    fun activeConfig(kind: ServerKind): ServerConfig? = active(kind)?.config

    /**
     * 新增或更新一份配置档案。
     *
     * 已存在同 [ServerProfile.id] 时原地更新，否则追加；
     * 该类型下还没有生效配置时，自动把这份配置设为当前生效。
     */
    fun saveProfile(kind: ServerKind, profile: ServerProfile) {
        val current = state(kind)
        val index = current.profiles.indexOfFirst { it.id == profile.id }
        val profiles = if (index >= 0) {
            current.profiles.toMutableList().also { it[index] = profile }
        } else {
            current.profiles + profile
        }
        val selectedId = current.selectedId?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        update(kind, ServerNodeState(profiles = profiles, selectedId = selectedId))
    }

    /** 删除一份配置档案；若删除的是当前生效项，则自动切到剩下的第一份。 */
    fun deleteProfile(kind: ServerKind, id: String) {
        val current = state(kind)
        val profiles = current.profiles.filterNot { it.id == id }
        if (profiles.size == current.profiles.size) return
        val selectedId = when {
            current.selectedId != id -> current.selectedId
            else -> profiles.firstOrNull()?.id
        }
        update(kind, ServerNodeState(profiles = profiles, selectedId = selectedId))
    }

    /** 切换当前生效的配置档案。 */
    fun selectProfile(kind: ServerKind, id: String) {
        val current = state(kind)
        if (current.profiles.none { it.id == id }) return
        if (current.selectedId == id) return
        update(kind, current.copy(selectedId = id))
    }

    private fun update(kind: ServerKind, state: ServerNodeState) {
        persist(kind, state)
        _states.value = _states.value + (kind to state)
    }

    private fun persist(kind: ServerKind, state: ServerNodeState) {
        prefs.edit()
            .putString(keyProfiles(kind), toJson(state.profiles))
            .putString(keySelected(kind), state.selectedId)
            .apply()
    }

    private fun readAll(): Map<ServerKind, ServerNodeState> {
        migrateLegacyIfNeeded()
        return ServerKind.entries.associateWith { read(it) }
    }

    private fun read(kind: ServerKind): ServerNodeState {
        val profiles = fromJson(prefs.getString(keyProfiles(kind), null))
        val storedSelected = prefs.getString(keySelected(kind), null)
        val selectedId = storedSelected?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.firstOrNull()?.id
        return ServerNodeState(profiles = profiles, selectedId = selectedId)
    }

    /**
     * 旧版本只保存单份配置（无配置名）。首次启动新版本时，
     * 把它迁移成内容服务节点下名为“默认配置”的档案，避免用户配置丢失。
     */
    private fun migrateLegacyIfNeeded() {
        migrateContentKeysToCms()
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val editor = prefs.edit().putBoolean(KEY_MIGRATED, true)
        val alreadyPresent = prefs.getString(keyProfiles(ServerKind.CMS), null) != null
        if (!alreadyPresent && prefs.contains(KEY_LEGACY_MODE)) {
            val legacy = ServerProfile(
                id = UUID.randomUUID().toString(),
                name = "默认配置",
                config = ServerConfig(
                    mode = prefs.getString(KEY_LEGACY_MODE, null)
                        ?.let { runCatching { ServerMode.valueOf(it) }.getOrNull() }
                        ?: ServerMode.DOMAIN,
                    domain = prefs.getString(KEY_LEGACY_DOMAIN, null)
                        ?: ServerConfig.DEFAULT_DOMAIN,
                    useHttps = prefs.getBoolean(KEY_LEGACY_HTTPS, true),
                    ip = prefs.getString(KEY_LEGACY_IP, "").orEmpty(),
                    port = prefs.getInt(KEY_LEGACY_PORT, ServerConfig.DEFAULT_PORT),
                ),
            )
            editor.putString(keyProfiles(ServerKind.CMS), toJson(listOf(legacy)))
            editor.putString(keySelected(ServerKind.CMS), legacy.id)
        }
        editor.apply()
    }

    /**
     * 枚举值 CONTENT 更名为 CMS 后，把旧的存储键内容搬到新键，
     * 避免已保存的配置因键名变化而丢失。
     */
    private fun migrateContentKeysToCms() {
        if (prefs.contains(keyProfiles(ServerKind.CMS))) return
        val legacyProfiles = prefs.getString(KEY_LEGACY_PROFILES_CONTENT, null) ?: return
        val editor = prefs.edit().putString(keyProfiles(ServerKind.CMS), legacyProfiles)
        prefs.getString(KEY_LEGACY_SELECTED_CONTENT, null)?.let { selected ->
            editor.putString(keySelected(ServerKind.CMS), selected)
        }
        editor.apply()
    }

    private fun toJson(profiles: List<ServerProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put(FIELD_ID, profile.id)
                    .put(FIELD_NAME, profile.name)
                    .put(FIELD_MODE, profile.config.mode.name)
                    .put(FIELD_DOMAIN, profile.config.domain)
                    .put(FIELD_HTTPS, profile.config.useHttps)
                    .put(FIELD_IP, profile.config.ip)
                    .put(FIELD_PORT, profile.config.port)
                    .put(FIELD_ACCESS_KEY, profile.config.accessKey)
                    .put(FIELD_SECRET_KEY, profile.config.secretKey),
            )
        }
        return array.toString()
    }

    private fun fromJson(raw: String?): List<ServerProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: continue
                    add(
                        ServerProfile(
                            id = id,
                            name = item.optString(FIELD_NAME).ifBlank { "配置" },
                            config = ServerConfig(
                                mode = runCatching {
                                    ServerMode.valueOf(item.optString(FIELD_MODE))
                                }.getOrDefault(ServerMode.DOMAIN),
                                domain = item.optString(FIELD_DOMAIN)
                                    .ifBlank { ServerConfig.DEFAULT_DOMAIN },
                                useHttps = item.optBoolean(FIELD_HTTPS, true),
                                ip = item.optString(FIELD_IP),
                                port = item.optInt(FIELD_PORT, ServerConfig.DEFAULT_PORT),
                                accessKey = item.optString(FIELD_ACCESS_KEY),
                                secretKey = item.optString(FIELD_SECRET_KEY),
                            ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun keyProfiles(kind: ServerKind) = "profiles_${kind.name.lowercase()}"

    private fun keySelected(kind: ServerKind) = "selected_${kind.name.lowercase()}"

    companion object {
        private const val PREFS_NAME = "amc_server_config"
        private const val KEY_MIGRATED = "migrated_to_profiles"
        // CONTENT → CMS 改名前的存储键
        private const val KEY_LEGACY_PROFILES_CONTENT = "profiles_content"
        private const val KEY_LEGACY_SELECTED_CONTENT = "selected_content"

        private const val KEY_LEGACY_MODE = "mode"
        private const val KEY_LEGACY_DOMAIN = "domain"
        private const val KEY_LEGACY_HTTPS = "use_https"
        private const val KEY_LEGACY_IP = "ip"
        private const val KEY_LEGACY_PORT = "port"

        private const val FIELD_ID = "id"
        private const val FIELD_NAME = "name"
        private const val FIELD_MODE = "mode"
        private const val FIELD_DOMAIN = "domain"
        private const val FIELD_HTTPS = "useHttps"
        private const val FIELD_IP = "ip"
        private const val FIELD_PORT = "port"
        private const val FIELD_ACCESS_KEY = "accessKey"
        private const val FIELD_SECRET_KEY = "secretKey"

        @Volatile
        private var instance: ServerConfigRepository? = null

        fun getInstance(context: Context): ServerConfigRepository =
            instance ?: synchronized(this) {
                instance ?: ServerConfigRepository(context.applicationContext).also { instance = it }
            }
    }
}
