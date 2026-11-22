package io.github.acnxo.amc.ui.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import io.github.acnxo.amc.data.todo.TodoItem
import io.github.acnxo.amc.data.todo.TodoTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 待办编辑对话框。
 *
 * - 右下角「完成」保存并退出；
 * - 「取消」或点击对话框外部区域即放弃编辑；
 * - 提醒时间同时写入服务端截止时间（gmt_deadline）并注册本地闹钟。
 */
@Composable
fun TodoEditDialog(
    initial: TodoItem?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        content: String,
        deadline: Date?,
        importance: Int?,
        urgency: Int?,
    ) -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var content by rememberSaveable { mutableStateOf(initial?.content.orEmpty()) }
    var deadlineMillis by rememberSaveable {
        mutableStateOf(TodoTime.parseUtc(initial?.gmtDeadline)?.time)
    }
    var importance by rememberSaveable { mutableStateOf(initial?.levelImportance ?: 50) }
    var urgency by rememberSaveable { mutableStateOf(initial?.levelUrgency ?: 50) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(text = if (initial == null) "新建待办" else "编辑待办")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "标题") },
                    minLines = 1,
                    maxLines = 4,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "事项内容") },
                    minLines = 3,
                    maxLines = 6,
                )
                LevelStepper(
                    label = "重要性",
                    value = importance,
                    onValueChange = { importance = it },
                )
                LevelStepper(
                    label = "紧急程度",
                    value = urgency,
                    onValueChange = { urgency = it },
                )
                if (initial != null) {
                    Text(
                        text = "服务端 Modify 暂不支持修改重要性与紧急程度，已有事项的改动不会生效。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "提醒时间",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = deadlineMillis?.let { formatLocal(it) } ?: "未设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        if (deadlineMillis != null) {
                            TextButton(onClick = { deadlineMillis = null }) {
                                Text(text = "清除")
                            }
                        }
                        TextButton(
                            onClick = {
                                pickDateTime(context, deadlineMillis) { picked ->
                                    deadlineMillis = picked
                                }
                            },
                        ) {
                            Text(text = if (deadlineMillis == null) "设置" else "修改")
                        }
                    }
                }
                if (initial != null && deadlineMillis == null && initial.gmtDeadline != null) {
                    Text(
                        text = "服务端不支持清空截止时间，清除后仅本机立即取消提醒，服务端仍保留原时间。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title.trim(),
                        content.trim(),
                        deadlineMillis?.let { Date(it) },
                        importance,
                        urgency,
                    )
                },
                enabled = !saving && title.isNotBlank(),
            ) {
                Text(text = "完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(text = "取消")
            }
        },
    )
}

private fun pickDateTime(context: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply {
        initial?.let { timeInMillis = it }
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    onPicked(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true,
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private fun formatLocal(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/** 0-100 的加减波轮：左右按钮点按可单步调整，长按会连续加减。 */
@Composable
private fun LevelStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                icon = Icons.Filled.Remove,
                contentDescription = "减少$label",
                enabled = value > LEVEL_MIN,
                onStep = { onValueChange((value - 1).coerceIn(LEVEL_MIN, LEVEL_MAX)) },
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            StepButton(
                icon = Icons.Filled.Add,
                contentDescription = "增加$label",
                enabled = value < LEVEL_MAX,
                onStep = { onValueChange((value + 1).coerceIn(LEVEL_MIN, LEVEL_MAX)) },
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 按下即步进一次；保持按下 400ms 后开始连发，每 60ms 一次
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        onStep()
        delay(400)
        while (true) {
            onStep()
            delay(60)
        }
    }

    IconButton(
        onClick = {},
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

private const val LEVEL_MIN = 0
private const val LEVEL_MAX = 100
