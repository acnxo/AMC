package io.github.acnxo.amc.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.acnxo.amc.data.ServerConfig
import io.github.acnxo.amc.data.ServerConfigRepository
import io.github.acnxo.amc.data.ServerMode

/**
 * 服务器节点配置页。
 *
 * 支持域名 / IP + 端口两种模式，保存后写入本地，
 * 后续 API 客户端可直接读取 `ServerConfigRepository.current().baseUrl`。
 */
@Composable
fun ServerNodeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { ServerConfigRepository.getInstance(context) }
    val savedConfig by repository.config.collectAsState()

    var mode by rememberSaveable { mutableStateOf(savedConfig.mode) }
    var domain by rememberSaveable { mutableStateOf(savedConfig.domain) }
    var useHttps by rememberSaveable { mutableStateOf(savedConfig.useHttps) }
    var ip by rememberSaveable { mutableStateOf(savedConfig.ip) }
    var portText by rememberSaveable { mutableStateOf(savedConfig.port.toString()) }

    val draft = ServerConfig(
        mode = mode,
        domain = domain,
        useHttps = useHttps,
        ip = ip,
        port = portText.toIntOrNull() ?: 0,
    )
    val validationError = draft.validate()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = "服务器节点",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "配置云端服务的基础地址，后续 API 调用将使用该地址。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "连接模式",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ServerMode.DOMAIN,
                        onClick = { mode = ServerMode.DOMAIN },
                        label = { Text(text = "域名") },
                    )
                    FilterChip(
                        selected = mode == ServerMode.IP_PORT,
                        onClick = { mode = ServerMode.IP_PORT },
                        label = { Text(text = "IP + 端口") },
                    )
                }

                if (mode == ServerMode.DOMAIN) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "域名") },
                        placeholder = { Text(text = "acanx.com") },
                        singleLine = true,
                        isError = validationError != null,
                        supportingText = {
                            Text(text = validationError ?: "例如：api.acanx.com")
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
                        isError = validationError != null,
                        supportingText = {
                            Text(text = validationError ?: "IPv4 地址")
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
                        isError = validationError != null,
                        supportingText = {
                            Text(text = "范围 1-65535")
                        },
                    )
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

                HorizontalDivider()

                Column {
                    Text(
                        text = "基础地址（baseUrl）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = draft.baseUrl,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    repository.save(draft)
                    Toast.makeText(context, "服务器节点已保存", Toast.LENGTH_SHORT).show()
                },
                enabled = validationError == null,
            ) {
                Text(text = "保存")
            }
            OutlinedButton(
                onClick = {
                    val current = repository.current()
                    mode = current.mode
                    domain = current.domain
                    useHttps = current.useHttps
                    ip = current.ip
                    portText = current.port.toString()
                },
            ) {
                Text(text = "重置")
            }
        }

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
                Text(
                    text = savedConfig.baseUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (savedConfig.mode) {
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
