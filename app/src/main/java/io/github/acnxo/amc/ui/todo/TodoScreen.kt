package io.github.acnxo.amc.ui.todo

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.acnxo.amc.data.ServerConfigRepository
import io.github.acnxo.amc.data.ServerKind
import io.github.acnxo.amc.data.todo.TodoApi
import io.github.acnxo.amc.data.todo.TodoItem
import io.github.acnxo.amc.data.todo.TodoReminder
import io.github.acnxo.amc.data.todo.TodoTime
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    // 正在播放「完成」动画的条目，以及已经可以折叠消失的条目
    var completingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var hiddenIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
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
        if (item.isDone) {
            // 取消完成：直接恢复为待开始，无需动画
            scope.launch {
                saving = true
                runCatching { client.quickComplete(item.taskNo, complete = false) }
                    .onSuccess { refreshKey++ }
                    .onFailure { error = it.message ?: "状态更新失败" }
                saving = false
            }
            return
        }
        if (item.taskNo in completingIds) return

        // 完成动画三阶段：勾选 → 划线 → 折叠消失
        completingIds = completingIds + item.taskNo
        scope.launch {
            val request = async { runCatching { client.quickComplete(item.taskNo, complete = true) } }
            delay(COMPLETE_ANIM_MS)
            hiddenIds = hiddenIds + item.taskNo
            delay(REMOVE_ANIM_MS)
            request.await()
                .onSuccess { todoItems = todoItems.filterNot { it.taskNo == item.taskNo } }
                .onFailure {
                    hiddenIds = hiddenIds - item.taskNo
                    error = it.message ?: "标记完成失败"
                }
            completingIds = completingIds - item.taskNo
            hiddenIds = hiddenIds - item.taskNo
        }
    }

    fun delete(item: TodoItem) {
        // TODO(#17): 服务端尚未提供软删除端点，Modify 也不接受 flag_delete。
        // 接口就绪后改为调用 TodoApi.softDelete(item.taskNo) 并刷新列表。
        Toast.makeText(
            context,
            "后端尚未支持软删除，删除功能待服务端提供接口（issue #17）",
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
                            text = "点击右下角的「+」新建一条",
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
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(items = sorted, key = { it.taskNo }) { item ->
                            TodoRow(
                                item = item,
                                completing = item.taskNo in completingIds,
                                hidden = item.taskNo in hiddenIds,
                                swipeEnabled = swipeEnabledId == item.taskNo,
                                onToggle = { toggle(item) },
                                onOpen = {
                                    editing = item
                                    editorVisible = true
                                },
                                onLongPress = { swipeEnabledId = item.taskNo },
                                onDelete = { delete(item) },
                            )
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
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            // Material Design 2 风格：圆形按钮 + 层级阴影
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp,
            ),
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
    completing: Boolean,
    hidden: Boolean,
    swipeEnabled: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val revealWidth = with(density) { REVEAL_WIDTH_DP.dp.toPx() }
    var offsetX by remember(item.taskNo) { mutableFloatStateOf(0f) }

    val done = item.isDone || completing
    // 阶段 2：标题变灰，删除线从左向右延伸
    val titleColor by animateColorAsState(
        targetValue = if (done) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = STRIKE_ANIM_MS, delayMillis = STRIKE_DELAY_MS),
        label = "titleColor",
    )
    val strikeProgress by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = tween(durationMillis = STRIKE_ANIM_MS, delayMillis = STRIKE_DELAY_MS),
        label = "strikeProgress",
    )
    val strikeColor = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(swipeEnabled) {
        if (!swipeEnabled) offsetX = 0f
    }

    // 阶段 3：整条卡片折叠 + 淡出
    AnimatedVisibility(
        visible = !hidden,
        exit = shrinkVertically(animationSpec = tween(REMOVE_ANIM_MS)) +
            fadeOut(animationSpec = tween(REMOVE_ANIM_MS)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // 背景层：长按后左滑露出的红色删除区
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CARD_SHAPE)
                    .background(
                        if (swipeEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Transparent
                        },
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

            Card(
                shape = CARD_SHAPE,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
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
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 阶段 1：Checkbox 自身的勾选动画
                    Checkbox(
                        checked = done,
                        onCheckedChange = { if (!done) onToggle() },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 8.dp)
                            .combinedClickable(
                                onClick = onOpen,
                                onLongClick = onLongPress,
                            ),
                    ) {
                        Text(
                            text = item.title.ifBlank { "（无标题）" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                if (strikeProgress > 0f) {
                                    val y = size.height * 0.55f
                                    drawLine(
                                        color = strikeColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width * strikeProgress, y),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    )
                                }
                            },
                        )
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
    }
}

private val CARD_SHAPE = RoundedCornerShape(14.dp)
private const val REVEAL_WIDTH_DP = 88

// 完成动画时间线：勾选 → 划线 → 折叠消失
private const val STRIKE_DELAY_MS = 120
private const val STRIKE_ANIM_MS = 360
private const val COMPLETE_ANIM_MS = STRIKE_DELAY_MS + STRIKE_ANIM_MS + 120L
private const val REMOVE_ANIM_MS = 300
