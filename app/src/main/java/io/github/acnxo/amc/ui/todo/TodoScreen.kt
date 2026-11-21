package io.github.acnxo.amc.ui.todo

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.acnxo.amc.data.ServerConfigRepository
import io.github.acnxo.amc.data.ServerKind
import io.github.acnxo.amc.data.todo.TodoApi
import io.github.acnxo.amc.data.todo.TodoItem
import io.github.acnxo.amc.data.todo.TodoReminder
import io.github.acnxo.amc.data.todo.TodoTime
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

/**
 * 待办事项页（复刻小米便签「待办」的交互）。
 *
 * - 数据来自「内容服务节点」配置的 BASE_URL 下的 CMS ToDo OpenAPI；
 * - 左侧勾选框切换完成状态；
 * - 点击事项弹出编辑对话框，可改标题、内容与提醒时间；
 * - 长按标题后左滑露出红色删除按钮（服务端暂无删除接口，点击会提示）。
 */
@Composable
fun TodoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configRepository = remember { ServerConfigRepository.getInstance(context) }
    val configStates by configRepository.states.collectAsState()
    val baseUrl = configStates[ServerKind.CMS]?.selected?.config?.BASE_URL.orEmpty()
    val api = remember(baseUrl) {
        baseUrl.takeIf { it.isNotBlank() }?.let { TodoApi(it) }
    }

    var todoItems by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var swipeEnabledId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(api, refreshKey) {
        val client = api ?: return@LaunchedEffect
        loading = true
        error = null
        runCatching { client.page() }
            .onSuccess { todoItems = it.items }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    fun save(title: String, content: String, deadline: Date?) {
        val client = api ?: return
        val current = editing
        scope.launch {
            saving = true
            error = null
            runCatching {
                val saved = if (current == null) {
                    client.create(title = title, content = content, deadline = deadline)
                } else {
                    client.modify(
                        taskNo = current.taskNo,
                        title = title,
                        content = content,
                        deadline = deadline,
                    )
                }
                if (deadline != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !TodoReminder.canPostNotifications(context)
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    TodoReminder.schedule(
                        context = context,
                        taskNo = saved.taskNo,
                        title = saved.title,
                        content = saved.content,
                        triggerAtMillis = deadline.time,
                    )
                } else {
                    TodoReminder.cancel(context, saved.taskNo)
                }
                saved
            }.onSuccess {
                editorVisible = false
                refreshKey++
            }.onFailure {
                error = it.message ?: "保存失败"
            }
            saving = false
        }
    }

    fun toggle(item: TodoItem) {
        val client = api ?: return
        scope.launch {
            saving = true
            runCatching { client.quickComplete(item.taskNo, complete = !item.isDone) }
                .onSuccess { refreshKey++ }
                .onFailure { error = it.message ?: "状态更新失败" }
            saving = false
        }
    }

    fun delete(item: TodoItem) {
        // 现有 OpenAPI 未提供删除端点，flag_delete 也不可通过 Modify 修改
        Toast.makeText(
            context,
            "服务端暂未提供删除接口，暂时无法删除「${item.title}」",
            Toast.LENGTH_LONG,
        ).show()
        swipeEnabledId = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (api == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "尚未配置内容服务节点",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请先在「设置 → 服务 → 内容服务节点」中配置基础地址（BASE_URL）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "未完成 ${todoItems.count { !it.isDone }} 项 · 共 ${todoItems.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { refreshKey++ }, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }

            error?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                loading && todoItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                todoItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "还没有待办事项",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击左下角的「+」新建一条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    val sorted = todoItems.sortedWith(
                        compareBy<TodoItem> { it.isDone }.thenBy { it.gmtDeadline ?: "9999" },
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        items(items = sorted, key = { it.taskNo }) { item ->
                            TodoRow(
                                item = item,
                                swipeEnabled = swipeEnabledId == item.taskNo,
                                onToggle = { toggle(item) },
                                onOpen = {
                                    editing = item
                                    editorVisible = true
                                },
                                onLongPress = { swipeEnabledId = item.taskNo },
                                onDelete = { delete(item) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editing = null
                editorVisible = true
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新增待办")
        }
    }

    if (editorVisible) {
        TodoEditDialog(
            initial = editing,
            saving = saving,
            onDismiss = { editorVisible = false },
            onConfirm = { title, content, deadline ->
                if (title.isBlank()) {
                    Toast.makeText(context, "标题不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    save(title, content, deadline)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoRow(
    item: TodoItem,
    swipeEnabled: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val revealWidth = with(density) { REVEAL_WIDTH_DP.dp.toPx() }
    var offsetX by remember(item.taskNo) { mutableFloatStateOf(0f) }

    LaunchedEffect(swipeEnabled) {
        if (!swipeEnabled) offsetX = 0f
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景层：左滑后露出的红色删除区
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    if (swipeEnabled) MaterialTheme.colorScheme.error else Color.Transparent,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (swipeEnabled) {
                IconButton(
                    onClick = onDelete,
                    enabled = offsetX <= -revealWidth * 0.9f,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(swipeEnabled) {
                    if (!swipeEnabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < -revealWidth / 2) -revealWidth else 0f
                        },
                        onDragCancel = { offsetX = 0f },
                    ) { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-revealWidth, 0f)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggle() },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = onLongPress,
                    ),
            ) {
                Text(
                    text = item.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                    color = if (item.isDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.content?.takeIf { it.isNotBlank() }?.let { content ->
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.gmtDeadline?.let { deadline ->
                    Text(
                        text = "提醒：${TodoTime.formatLocal(deadline)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private const val REVEAL_WIDTH_DP = 88
