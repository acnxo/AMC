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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
 * 编辑对话框的产出，直接对应 `Create` / `Modify` 的入参。
 *
 * - [clearDeadline] 为 true 时服务端通过 `flag_clear_deadline` 把截止时间置空；
 * - [progress] 仅在编辑已有事项时传值，创建接口会忽略 `percent_completion`。
 */
data class TodoEditResult(
    val title: String,
    val content: String,
    val deadline: Date?,
    val clearDeadline: Boolean,
    val importance: Int,
    val urgency: Int,
    val minuteEstimate: Int?,
    val progress: Int?,
)

/**
 * 待办编辑对话框。
 *
 * - 右下角「完成」保存并退出；
 * - 「取消」或点击对话框外部区域即放弃编辑；
 * - 完成截止时间同时写入服务端 `gmt_deadline` 并注册本地提醒。
 */
@Composable
fun TodoEditDialog(
    initial: TodoItem?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TodoEditResult) -> Unit,
) {
    val context = LocalContext.current
    val editing = initial != null
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var content by rememberSaveable { mutableStateOf(initial?.content.orEmpty()) }
    var deadlineMillis by rememberSaveable {
        mutableStateOf(TodoTime.parseUtc(initial?.gmtDeadline)?.time)
    }
    var importance by rememberSaveable {
        mutableStateOf(initial?.levelImportance ?: DEFAULT_LEVEL)
    }
    var urgency by rememberSaveable {
        mutableStateOf(initial?.levelUrgency ?: DEFAULT_LEVEL)
    }
    var minuteEstimate by rememberSaveable {
        mutableStateOf(initial?.minuteEstimate?.toString().orEmpty())
    }
    var progress by rememberSaveable {
        mutableStateOf(initial?.percentCompletion ?: 0)
    }
    // 初始是否已有截止时间：用于判断「清空」是否真的需要通知服务端
    val hadDeadline = remember(initial?.taskNo) { initial?.gmtDeadline != null }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(text = if (editing) "编辑待办" else "新建待办")
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
                OutlinedTextField(
                    value = minuteEstimate,
                    onValueChange = { value ->
                        minuteEstimate = value.filter { it.isDigit() }.take(MAX_MINUTE_DIGITS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "预计耗时（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = if (editing) {
                                "留空表示不修改"
                            } else {
                                "留空时由服务端按缺省值 $DEFAULT_MINUTE_ESTIMATE 分钟填充"
                            },
                        )
                    },
                )
                if (editing) {
                    LevelStepper(
                        label = "完成进度",
                        value = progress,
                        onValueChange = { progress = it },
                    )
                    Text(
                        text = "进度改为 100% 会自动标记为已完成，改回 0% 则回到待开始并清空完成时间。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "完成截止时间",
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
                                Text(text = "清空")
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
                Text(
                    text = "截止时间同时用于本机提醒；清空会同步通知服务端置空该字段。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        TodoEditResult(
                            title = title.trim(),
                            content = content.trim(),
                            deadline = deadlineMillis?.let { Date(it) },
                            // 原本有截止时间、这次被清空时，才需要显式通知服务端
                            clearDeadline = editing && hadDeadline && deadlineMillis == null,
                            importance = importance,
                            urgency = urgency,
                            minuteEstimate = minuteEstimate.toIntOrNull()?.coerceAtLeast(0),
                            progress = if (editing) progress else null,
                        ),
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

/** 服务端未指定时的缺省指标值，与 Create 的缺省保持一致。 */
private const val DEFAULT_LEVEL = 50
private const val DEFAULT_MINUTE_ESTIMATE = 10
private const val MAX_MINUTE_DIGITS = 5
