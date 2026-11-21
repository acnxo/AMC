package io.github.acnxo.amc.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.acnxo.amc.BuildConfig
import io.github.acnxo.amc.data.ApkInstaller
import io.github.acnxo.amc.data.PackageType
import io.github.acnxo.amc.data.ReleaseAsset
import io.github.acnxo.amc.data.ReleaseInfo
import io.github.acnxo.amc.data.UpdateChannel
import io.github.acnxo.amc.data.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * 版本更新页。
 *
 * 从 GitHub Releases 读取三个通道（正式版 / 测试版 / 快照版）的版本列表，
 * 用户选择包类型（APK / AAB）后可下载并交给系统安装器升级或降级。
 *
 * 注意：AAB 是应用商店分发格式，无法在设备上直接安装，只能下载；
 * 降级安装与包签名不一致是否被接受由系统安装器决定。
 */
@Composable
fun UpdateScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { UpdateRepository() }
    val scope = rememberCoroutineScope()

    var channel by rememberSaveable { mutableStateOf(UpdateChannel.STABLE) }
    var packageType by rememberSaveable { mutableStateOf(PackageType.APK) }

    var releases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }

    var downloadingTag by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    // 下载回调发生在 IO 线程，用 StateFlow 中转，避免跨线程改 Compose 状态
    val progressFlow = remember { MutableStateFlow(0f) }
    val progress by progressFlow.collectAsState()

    LaunchedEffect(refreshToken) {
        isLoading = true
        loadError = null
        runCatching { repository.fetchReleases() }
            .onSuccess { releases = it }
            .onFailure { loadError = it.message ?: "无法获取版本列表" }
        isLoading = false
    }

    fun downloadAndInstall(release: ReleaseInfo, asset: ReleaseAsset) {
        scope.launch {
            statusMessage = null
            downloadingTag = release.tag
            progressFlow.value = 0f
            try {
                val target = File(ApkInstaller.downloadDir(context), asset.name)
                val cached = target.exists() &&
                    (asset.size <= 0 || target.length() == asset.size)

                val file = if (cached) {
                    target
                } else {
                    repository.download(asset, target) { done, total ->
                        val value = if (total > 0) done.toFloat() / total else 0f
                        progressFlow.value = value.coerceIn(0f, 1f)
                    }
                }

                when (packageType) {
                    PackageType.AAB -> {
                        statusMessage = "AAB 已下载到 ${file.absolutePath}。" +
                            "AAB 是应用商店分发格式，无法直接安装，请使用 bundletool 转换或通过应用商店分发。"
                    }

                    PackageType.APK -> {
                        if (!ApkInstaller.canInstall(context)) {
                            statusMessage = "请先允许本应用安装未知来源应用，授权后再点一次安装。"
                            ApkInstaller.openUnknownSourcesSettings(context)
                        } else {
                            ApkInstaller.installApk(context, file)
                            statusMessage = "已调起系统安装器，请按提示确认安装。"
                        }
                    }
                }
            } catch (e: Exception) {
                statusMessage = "下载失败：${e.message ?: "未知错误"}"
            } finally {
                downloadingTag = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = "版本更新",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "当前版本：${BuildConfig.VERSION_NAME}（${BuildConfig.GIT_SHA.take(7)}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 通道
        Text(
            text = "更新通道",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateChannel.entries.forEach { item ->
                FilterChip(
                    selected = channel == item,
                    onClick = { channel = item },
                    label = { Text(text = item.label) },
                )
            }
        }
        Text(
            text = channel.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 包类型
        Text(
            text = "安装包类型",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PackageType.entries.forEach { item ->
                FilterChip(
                    selected = packageType == item,
                    onClick = { packageType = item },
                    label = { Text(text = item.label) },
                )
            }
        }
        if (packageType == PackageType.AAB) {
            Text(
                text = "AAB 无法直接安装，只能下载；需要 APK 才能完成升级/降级。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "可用版本",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick = { refreshToken++ },
                enabled = !isLoading && downloadingTag == null,
            ) {
                Text(text = "刷新")
            }
        }

        loadError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val visible = releases.filter {
            it.channel == channel && it.assetFor(packageType) != null
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                visible.isEmpty() -> {
                    Text(
                        text = if (packageType == PackageType.AAB) {
                            "该通道暂未提供 AAB 包，可切换为 APK。"
                        } else {
                            "该通道暂无可安装版本。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = visible, key = { it.tag }) { release ->
                            ReleaseCard(
                                release = release,
                                packageType = packageType,
                                downloading = downloadingTag == release.tag,
                                downloadProgress = progress,
                                installEnabled = downloadingTag == null,
                                onInstall = {
                                    release.assetFor(packageType)?.let { asset ->
                                        downloadAndInstall(release, asset)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseInfo,
    packageType: PackageType,
    downloading: Boolean,
    downloadProgress: Float,
    installEnabled: Boolean,
    onInstall: () -> Unit,
) {
    val asset = release.assetFor(packageType)
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = release.versionName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = release.channel.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "发布时间：${release.publishedAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (asset != null) {
                Text(
                    text = "${packageType.label} · ${formatSize(asset.size)} · ${asset.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (downloading) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "下载中 ${(downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onInstall,
                enabled = installEnabled && asset != null,
            ) {
                Text(
                    text = when (packageType) {
                        PackageType.APK -> "下载并安装"
                        PackageType.AAB -> "下载 AAB"
                    },
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "大小未知"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb)
    }
}
