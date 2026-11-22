package io.github.acnxo.amc.ui.settings

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.acnxo.amc.data.ServerConfig
import io.github.acnxo.amc.data.ServerConfigRepository
import io.github.acnxo.amc.data.ServerKind
import io.github.acnxo.amc.data.ServerMode
import io.github.acnxo.amc.data.ServerNodeState
import io.github.acnxo.amc.data.ServerProfile
import io.github.acnxo.amc.data.openapi.NodeProbe
import io.github.acnxo.amc.data.openapi.NodeProbeReport
import io.github.acnxo.amc.data.openapi.ProbeResult
import kotlinx.coroutines.launch
import java.util.UUID

/** 服务节点页标题。 */
private val ServerKind.pageTitle: String
    get() = when (this) {
        ServerKind.CMS -> "内容服务节点"
        ServerKind.FINV -> "Finv服务节点"
    }

/**
 * 服务节点配置页。
 *
 * 内容服务与 Finv 服务共用本页面，按 [kind] 区分各自的配置集合。
 * 每个集合可以保存多份带配置名的档案（profile），点击切换当前生效项，
 * 长按某一份可删除。保存后写入本地，后续 API 客户端可直接读取：
 *
 * ```kotlin
 * ServerConfigRepository.getInstance(context).activeConfig(ServerKind.CMS)?.BASE_URL
 * ```
 */
@Composable
fun ServerNodeScreen(
    kind: ServerKind,
    modifier: Modifier = Modifier,
) {
    // 切换菜单时重建页面状态，避免把上一种服务的草稿带过来
    key(kind) {
        ServerNodeContent(kind = kind, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ServerNodeContent(
    kind: ServerKind,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { ServerConfigRepository.getInstance(context) }
    val states by repository.states.collectAsState()
    val nodeState = states[kind] ?: ServerNodeState()
    val selectedProfile = nodeState.selected

    // 编辑中的表单；editingId 为 null 表示“正在新增”而非编辑已有配置
    var editingId by rememberSaveable { mutableStateOf(selectedProfile?.id) }
    var name by rememberSaveable { mutableStateOf(selectedProfile?.name.orEmpty()) }
    var mode by rememberSaveable {
        mutableStateOf(selectedProfile?.config?.mode ?: ServerMode.DOMAIN)
    }
    var useHttps by rememberSaveable {
        mutableStateOf(selectedProfile?.config?.useHttps ?: true)
    }
    var domain by rememberSaveable {
        mutableStateOf(selectedProfile?.config?.domain ?: ServerConfig.DEFAULT_DOMAIN)
    }
    var ip by rememberSaveable { mutableStateOf(selectedProfile?.config?.ip.orEmpty()) }
    var portText by rememberSaveable {
        mutableStateOf(
            (selectedProfile?.config?.port ?: ServerConfig.DEFAULT_PORT).toString(),
        )
    }
    // OpenAPI 鉴权凭证（仅内容服务节点使用）
    var accessKey by rememberSaveable {
        mutableStateOf(selectedProfile?.config?.accessKey.orEmpty())
    }
    var secretKey by rememberSaveable {
        mutableStateOf(selectedProfile?.config?.secretKey.orEmpty())
    }
    // SK 默认隐藏，可手动切换显示
    var secretVisible by rememberSaveable { mutableStateOf(false) }

    // 长按后弹出上下文菜单的配置 id
    var menuProfileId by remember { mutableStateOf<String?>(null) }
    // 连接模式下拉是否展开
    var modeMenuExpanded by remember { mutableStateOf(false) }

    fun loadProfile(profile: ServerProfile?) {
        editingId = profile?.id
        name = profile?.name.orEmpty()
        mode = profile?.config?.mode ?: ServerMode.DOMAIN
        useHttps = profile?.config?.useHttps ?: true
        domain = profile?.config?.domain ?: ServerConfig.DEFAULT_DOMAIN
        ip = profile?.config?.ip.orEmpty()
        portText = (profile?.config?.port ?: ServerConfig.DEFAULT_PORT).toString()
        accessKey = profile?.config?.accessKey.orEmpty()
        secretKey = profile?.config?.secretKey.orEmpty()
        secretVisible = false
    }

    fun startNewProfile() {
        loadProfile(null)
        name = "配置 ${nodeState.profiles.size + 1}"
    }

    val draft = ServerConfig(
        mode = mode,
        domain = domain,
        useHttps = useHttps,
        ip = ip,
        port = portText.toIntOrNull() ?: 0,
        accessKey = accessKey.trim(),
        secretKey = secretKey.trim(),
    )
    val nameError = if (name.isBlank()) "请输入配置名" else null
    val configError = draft.validate()
    // AK/SK 可以都留空（不签名），但只填一个视为未填完
    val credentialsError = when {
        accessKey.isBlank() && secretKey.isBlank() -> null
        accessKey.isBlank() -> "已填写 SK，请补全 AK"
        secretKey.isBlank() -> "已填写 AK，请补全 SK"
        else -> null
    }
    val canSave = nameError == null && configError == null &&
        (kind != ServerKind.CMS || credentialsError == null)

    // 节点测试：用当前生效配置探测「服务存活」与「鉴权握手」
    var probing by remember { mutableStateOf(false) }
    var probeReport by remember { mutableStateOf<NodeProbeReport?>(null) }
    val probeScope = rememberCoroutineScope()

    // 切换生效配置后，之前的测试结果不再适用
    LaunchedEffect(nodeState.selectedId) {
        probeReport = null
        probing = false
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = kind.pageTitle,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "可保存多份配置，点击切换当前生效项，长按配置可删除。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------------------------------------------------------
        // 已添加的配置：点击切换，长按删除
        // ---------------------------------------------------------------
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "已添加配置",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (nodeState.profiles.isEmpty()) {
                    Text(
                        text = "还没有配置，点下方按钮新增一份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                nodeState.profiles.forEach { profile ->
                    val isActive = profile.id == nodeState.selectedId
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (isActive) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                )
                                .combinedClickable(
                                    onClick = {
                                        loadProfile(profile)
                                        repository.selectProfile(kind, profile.id)
                                    },
                                    onLongClick = { menuProfileId = profile.id },
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isActive, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = profile.config.BASE_URL +
                                        if (profile.config.hasCredentials) " · 已配置 AK/SK" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = menuProfileId == profile.id,
                            onDismissRequest = { menuProfileId = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "删除") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuProfileId = null
                                    repository.deleteProfile(kind, profile.id)
                                    if (editingId == profile.id) {
                                        // 删除的是正在编辑的配置，切到删除后的生效项
                                        loadProfile(repository.state(kind).selected)
                                    }
                                    Toast.makeText(
                                        context,
                                        "已删除配置：${profile.name}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { startNewProfile() }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "新增配置")
                }
            }
        }

        // ---------------------------------------------------------------
        // 节点测试：存活探测 + 鉴权握手
        // ---------------------------------------------------------------
        if (kind == ServerKind.CMS) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 取一份本地引用，便于在下面的分支中安全使用
                    val profile = selectedProfile
                    Text(
                        text = "节点测试",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "使用当前生效配置探测服务连通性，两项都通过才表示节点可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (profile == null) {
                        Text(
                            text = "当前还没有生效配置，请先新增并保存一份配置。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ProbeRow(
                            title = NodeProbe.TITLE_LIVE,
                            path = NodeProbe.PATH_LIVE,
                            result = probeReport?.live,
                            running = probing,
                        )
                        ProbeRow(
                            title = NodeProbe.TITLE_HANDSHAKE,
                            path = NodeProbe.PATH_HANDSHAKE,
                            result = probeReport?.handshake,
                            running = probing,
                        )
                        probeReport?.let { report ->
                            Text(
                                text = if (report.allSuccess) {
                                    "连通性正常，可以开始使用"
                                } else {
                                    "连通性异常，请按上方提示检查配置"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (report.allSuccess) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    val config = profile.config
                                    probing = true
                                    probeReport = null
                                    probeScope.launch {
                                        try {
                                            probeReport = NodeProbe.run(config)
                                        } finally {
                                            probing = false
                                        }
                                    }
                                },
                                enabled = !probing,
                            ) {
                                Text(text = if (probing) "测试中…" else "开始测试")
                            }
                            if (probing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------
        // 配置表单
        // ---------------------------------------------------------------
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (editingId == null) "新增配置" else "编辑配置",
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "配置名") },
                    placeholder = { Text(text = "例如：公司内网") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = {
                        Text(text = nameError ?: "用于区分不同的配置档案")
                    },
                )

                // 连接模式：下拉选择
                ExposedDropdownMenuBox(
                    expanded = modeMenuExpanded,
                    onExpandedChange = { modeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = mode.label,
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text(text = "连接模式") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = modeMenuExpanded,
                            )
                        },
                    )
                    ExposedDropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false },
                    ) {
                        ServerMode.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(text = item.label) },
                                onClick = {
                                    mode = item
                                    modeMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "使用 HTTPS",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "关闭后使用 http 协议",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useHttps,
                        onCheckedChange = { useHttps = it },
                    )
                }

                if (mode == ServerMode.DOMAIN) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "域名") },
                        placeholder = { Text(text = "example.com") },
                        singleLine = true,
                        isError = configError != null,
                        supportingText = {
                            Text(text = configError ?: "例如：api.example.com")
                        },
                    )
                } else {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "IP 地址") },
                        placeholder = { Text(text = "192.168.1.10") },
                        singleLine = true,
                        isError = configError != null,
                        supportingText = {
                            Text(text = configError ?: "IPv4 地址")
                        },
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { value -> portText = value.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "端口") },
                        placeholder = { Text(text = "8080") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = configError != null,
                        supportingText = {
                            Text(text = "范围 1-65535")
                        },
                    )
                }

                if (kind == ServerKind.CMS) {
                    HorizontalDivider()
                    Text(
                        text = "OpenAPI 鉴权（AK/SK）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "填写平台下发的凭证后，调用待办等开放接口会自动附带签名；" +
                            "留空表示不签名（仅适用于未开启鉴权的环境）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = accessKey,
                        onValueChange = { value -> accessKey = value.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "Access Key（AK）") },
                        placeholder = { Text(text = "平台下发的 AccessKeyId") },
                        singleLine = true,
                        isError = credentialsError != null,
                        supportingText = {
                            Text(text = credentialsError ?: "AK 与 SK 需成对填写")
                        },
                    )
                    OutlinedTextField(
                        value = secretKey,
                        onValueChange = { value -> secretKey = value.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "Secret Key（SK）") },
                        singleLine = true,
                        visualTransformation = if (secretVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { secretVisible = !secretVisible }) {
                                Icon(
                                    imageVector = if (secretVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (secretVisible) "隐藏 SK" else "显示 SK",
                                )
                            }
                        },
                        supportingText = {
                            Text(text = "仅保存在本机配置中，不会写入日志")
                        },
                    )
                }

                HorizontalDivider()

                Column {
                    Text(
                        text = "基础地址（BASE_URL）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = draft.BASE_URL,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val profile = ServerProfile(
                                id = editingId ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                config = draft,
                            )
                            repository.saveProfile(kind, profile)
                            editingId = profile.id
                            Toast.makeText(context, "已保存配置：${profile.name}", Toast.LENGTH_SHORT).show()
                        },
                        enabled = canSave,
                    ) {
                        Text(text = "保存")
                    }
                    OutlinedButton(
                        onClick = { loadProfile(repository.active(kind)) },
                    ) {
                        Text(text = "重置")
                    }
                }
            }
        }

        // ---------------------------------------------------------------
        // 当前生效配置
        // ---------------------------------------------------------------
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "当前生效配置",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (selectedProfile == null) {
                    Text(
                        text = "尚未配置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "${selectedProfile.name} · ${selectedProfile.config.BASE_URL}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (selectedProfile.config.mode) {
                            ServerMode.DOMAIN -> "模式：域名"
                            ServerMode.IP_PORT -> "模式：IP + 端口"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 单条探测结果：图标 + 名称 + 端点 + 摘要。 */
@Composable
private fun ProbeRow(
    title: String,
    path: String,
    result: ProbeResult?,
    running: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = when {
                result?.success == true -> Icons.Filled.CheckCircle
                result != null -> Icons.Filled.Cancel
                running -> Icons.Filled.Sync
                else -> Icons.Filled.Circle
            },
            contentDescription = null,
            tint = when {
                result?.success == true -> MaterialTheme.colorScheme.primary
                result != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = result?.summary ?: if (running) "测试中…" else "未测试",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    result?.success == true -> MaterialTheme.colorScheme.primary
                    result != null -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
