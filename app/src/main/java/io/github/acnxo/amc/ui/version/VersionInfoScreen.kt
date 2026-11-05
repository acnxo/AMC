package io.github.acnxo.amc.ui.version

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.acnxo.amc.BuildConfig

/**
 * 版本信息页：展示安装包版本与构建来源，便于测试时确认当前运行的是哪个构建。
 */
@Composable
fun VersionInfoScreen(modifier: Modifier = Modifier) {
    val rows = listOf(
        "版本名称" to BuildConfig.VERSION_NAME,
        "版本号（versionCode）" to BuildConfig.VERSION_CODE.toString(),
        "包名" to BuildConfig.APPLICATION_ID,
        "构建类型" to BuildConfig.BUILD_TYPE,
        "Git 提交" to BuildConfig.GIT_SHA,
        "构建时间" to BuildConfig.GIT_BUILD_TIME,
        "Android 版本" to "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
        "设备型号" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = "版本信息",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "当前安装包与构建来源信息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    ListItem(
                        headlineContent = { Text(text = label) },
                        supportingContent = {
                            Text(
                                text = value,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}
