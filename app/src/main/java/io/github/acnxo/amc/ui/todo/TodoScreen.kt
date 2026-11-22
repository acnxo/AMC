package io.github.acnxo.amc.ui.todo

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.acnxo.amc.data.ServerConfigRepository
import io.github.acnxo.amc.data.ServerKind
import io.github.acnxo.amc.data.todo.TodoApi
import io.github.acnxo.amc.data.todo.TodoContentType
import io.github.acnxo.amc.data.todo.TodoItem
import io.github.acnxo.amc.data.todo.TodoReminder
import io.github.acnxo.amc.data.todo.TodoTime
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 待办事项页（复刻小米便签「待办」的交互）。
 *
 * - 待办页分「待开始」「进行中」两个标签页，分别用 `filter=0` 与 `filter=2` 拉数据；
 * - 左侧勾选框切换完成状态；
 * - 点击标题展开/折叠详细描述（手风琴，同一时刻只展开一条）；
 * - 长按标题弹出编辑对话框，可改标题、内容、重要性、紧急程度、预计耗时、
 *   完成进度与完成截止时间；
 * - 左滑露出红色删除按钮，点击调用 `Modify` 写 `flag_delete=1` 做逻辑删除。
 */
@Composable
fun TodoScreen(
    showDone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configRepository = remember { ServerConfigRepository.getInstance(context) }
    val configStates by configRepository.states.collectAsState()
    val activeConfig = configStates[ServerKind.CMS]?.selected?.config
    val baseUrl = activeConfig?.BASE_URL.orEmpty()
    val accessKey = activeConfig?.accessKey.orEmpty()
    val secretKey = activeConfig?.secretKey.orEmpty()
    val api = remember(baseUrl, accessKey, secretKey) {
        baseUrl.takeIf { it.isNotBlank() }?.let { TodoApi(it, accessKey, secretKey) }
    }

    var todoItems by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    // 正在播放「完成 / 取消完成」动画的条目，以及已经可以折叠消失的条目
    var completingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var uncompletingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var hiddenIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    // 手风琴：同一时刻最多展开一条的详细描述
    var expandedId by remember { mutableStateOf<Long?>(null) }
    // 待办页按状态分两个标签页，各自走对应的服务端 filter
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = TodoTab.entries
    val currentTab = tabs[tabIndex.coerceIn(tabs.indices)]
    val currentFilter = if (showDone) TodoApi.FILTER_DONE else currentTab.filter
    val scope = rememberCoroutineScope()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(api, refreshKey, showDone, currentFilter) {
        val client = api ?: return@LaunchedEffect
        loading = true
        error = null
        runCatching { client.page(filter = currentFilter) }
            .onSuccess { page ->
                // 两个超期类标签页只留未完成的；已完成的一律剔掉，与服务端筛重配合避免误入口
                val expiredTab = currentTab == TodoTab.TIMEOUT || currentTab == TodoTab.OVERDUE
                todoItems = if (expiredTab) page.items.filterNot { it.isDone } else page.items
            }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    fun save(result: TodoEditResult) {
        val client = api ?: return
        val current = editing
        // 提到 lambda 外部，避免多次可空智能转换
        val deadline = result.deadline
        scope.launch {
            saving = true
            error = null
            runCatching {
                val saved = if (current == null) {
                    client.create(
                        title = result.title,
                        content = result.content,
                        deadline = deadline,
                        minuteEstimate = result.minuteEstimate,
                        levelImportance = result.importance,
                        levelUrgency = result.urgency,
                    )
                } else {
                    client.modify(
                        taskNo = current.taskNo,
                        title = result.title,
                        content = result.content,
                        deadline = deadline,
                        clearDeadline = result.clearDeadline,
                        minuteEstimate = result.minuteEstimate,
                        levelImportance = result.importance,
                        levelUrgency = result.urgency,
                        percentCompletion = result.progress,
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
                // 新建的事项固定为待开始；若当前在「进行中」标签页，切回待开始
                if (current == null) tabIndex = 0
                refreshKey++
            }.onFailure {
                error = it.message ?: "保存失败"
            }
            saving = false
        }
    }

    /** 待办页：标记完成（勾选变黄 → 划线 → 折叠消失）。 */
    fun complete(item: TodoItem) {
        val client = api ?: return
        if (item.taskNo in completingIds) return

        // 完成动画三阶段：勾选 → 划线 → 折叠消失
        completingIds = completingIds + item.taskNo
        scope.launch {
            val request = async { runCatching { client.quickComplete(item.taskNo, complete = true) } }
            delay(COMPLETE_ANIM_MS)
            hiddenIds = hiddenIds + item.taskNo
            delay(REMOVE_ANIM_MS.toLong())
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

    /** 已完成页：恢复为未完成（取消勾选 → 删除线收回 → 折叠消失）。 */
    fun uncomplete(item: TodoItem) {
        val client = api ?: return
        if (item.taskNo in uncompletingIds) return
        uncompletingIds = uncompletingIds + item.taskNo
        scope.launch {
            val request = async { runCatching { client.quickComplete(item.taskNo, complete = false) } }
            // 先等勾选框取消选中、删除线收回
            delay((STRIKE_DELAY_MS + STRIKE_ANIM_MS).toLong())
            hiddenIds = hiddenIds + item.taskNo
            delay(REMOVE_ANIM_MS.toLong())
            request.await()
                .onSuccess { todoItems = todoItems.filterNot { it.taskNo == item.taskNo } }
                .onFailure {
                    hiddenIds = hiddenIds - item.taskNo
                    error = it.message ?: "恢复为未完成失败"
                }
            uncompletingIds = uncompletingIds - item.taskNo
            hiddenIds = hiddenIds - item.taskNo
        }
    }

    /** 左滑删除：调用 `Modify` 写 `flag_delete=1` 做逻辑删除，成功后从列表移除。 */
    fun delete(item: TodoItem) {
        val client = api ?: return
        scope.launch {
            runCatching { client.softDelete(item.taskNo) }
                .onSuccess {
                    todoItems = todoItems.filterNot { it.taskNo == item.taskNo }
                    if (expandedId == item.taskNo) expandedId = null
                    Toast.makeText(context, "已删除：${item.title}", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error = it.message ?: "删除失败" }
        }
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
                    text = if (showDone) {
                        "已完成 ${todoItems.size} 项"
                    } else {
                        "${currentTab.label} ${todoItems.size} 项"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { refreshKey++ }, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }

            if (!showDone) {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, item ->
                        Tab(
                            selected = index == tabIndex,
                            onClick = {
                                if (index != tabIndex) {
                                    tabIndex = index
                                    expandedId = null
                                    todoItems = emptyList()
                                    error = null
                                }
                            },
                            text = { Text(text = item.label) },
                        )
                    }
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
                            text = if (showDone) {
                                "还没有已完成的事项"
                            } else {
                                "没有${currentTab.label}的事项"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!showDone) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右下角的「+」新建一条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    // 状态已由服务端 filter 决定，这里不再二次过滤，避免漏掉进行中事项
                    val sorted = todoItems.sortedWith(
                        compareBy<TodoItem> { it.isDone }
                            .thenByDescending { it.taskPriority }
                            .thenBy { it.gmtDeadline ?: "9999" },
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
                                uncompleting = item.taskNo in uncompletingIds,
                                hidden = item.taskNo in hiddenIds,
                                expanded = expandedId == item.taskNo,
                                onToggle = {
                                    if (item.isDone) uncomplete(item) else complete(item)
                                },
                                onToggleExpand = {
                                    expandedId = if (expandedId == item.taskNo) null else item.taskNo
                                },
                                onEdit = {
                                    editing = item
                                    editorVisible = true
                                },
                                onDelete = { delete(item) },
                            )
                        }
                    }
                }
            }
        }

        if (!showDone) {
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
    }

    if (editorVisible) {
        TodoEditDialog(
            initial = editing,
            saving = saving,
            onDismiss = { editorVisible = false },
            onConfirm = { result ->
                if (result.title.isBlank()) {
                    Toast.makeText(context, "标题不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    save(result)
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
    uncompleting: Boolean,
    hidden: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val revealWidth = with(density) { REVEAL_WIDTH_DP.dp.toPx() }
    var offsetX by remember(item.taskNo) { mutableFloatStateOf(0f) }

    // 取消完成时先让勾选框与删除线回退，动画结束后条目再折叠消失
    val done = (item.isDone || completing) && !uncompleting
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
    // 多行标题需要按行绘制删除线，这里保存实际排版结果
    var titleLayout by remember(item.taskNo) { mutableStateOf<TextLayoutResult?>(null) }

    // 阶段 3：整条卡片折叠 + 淡出
    AnimatedVisibility(
        visible = !hidden,
        exit = shrinkVertically(animationSpec = tween<IntSize>(REMOVE_ANIM_MS)) +
            fadeOut(animationSpec = tween<Float>(REMOVE_ANIM_MS)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // 背景层：左滑后露出的红色删除区（本身被卡片遮挡）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CARD_SHAPE)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
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

            Card(
                shape = CARD_SHAPE,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .pointerInput(Unit) {
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 阶段 1：勾选框选中——黄色强调色 + 勾号绘制动画
                        Checkbox(
                            checked = done,
                            // 状态由服务端与动画驱动，这里只负责把点击交回上层
                            onCheckedChange = { onToggle() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DONE_ACCENT,
                                checkmarkColor = DONE_CHECKMARK,
                                uncheckedColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp, end = 8.dp)
                                .combinedClickable(
                                    onClick = onToggleExpand,
                                    onLongClick = onEdit,
                                ),
                        ) {
                            Text(
                                text = item.title.ifBlank { "（无标题）" },
                                // 比 bodyLarge（16sp）大两级，突出标题
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 20.sp,
                                    lineHeight = 26.sp,
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = titleColor,
                                // 展开时完整展示标题（去掉行数限制与省略号），折叠时回到两行截断
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                                onTextLayout = { titleLayout = it },
                                modifier = Modifier
                                    .animateContentSize()
                                    .drawWithContent {
                                        drawContent()
                                        if (strikeProgress <= 0f) return@drawWithContent
                                        val layout = titleLayout ?: return@drawWithContent
                                        // 逐行绘制：每行单独取水平范围与垂直中线，多行时不会只划在首行
                                        for (line in 0 until layout.lineCount) {
                                            val top = layout.getLineTop(line)
                                            val bottom = layout.getLineBottom(line)
                                            val left = layout.getLineLeft(line)
                                            val right = layout.getLineRight(line)
                                            val y = (top + bottom) / 2f
                                            drawLine(
                                                color = strikeColor,
                                                start = Offset(left, y),
                                                end = Offset(left + (right - left) * strikeProgress, y),
                                                strokeWidth = 1.5.dp.toPx(),
                                                cap = StrokeCap.Round,
                                            )
                                        }
                                    },
                            )
                            item.gmtDeadline?.let { deadline ->
                                Text(
                                    text = "截止：${TodoTime.formatLocal(deadline)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // 手风琴详情区：无描述时保留一行空文本，展开/折叠带动画
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(
                            animationSpec = tween<IntSize>(EXPAND_ANIM_MS),
                        ) + fadeIn(animationSpec = tween<Float>(EXPAND_ANIM_MS)),
                        exit = shrinkVertically(
                            animationSpec = tween<IntSize>(EXPAND_ANIM_MS),
                        ) + fadeOut(animationSpec = tween<Float>(EXPAND_ANIM_MS)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = item.content?.takeIf { it.isNotBlank() } ?: " ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // 编号与内容类型：用于对照后台与报障
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Metric(label = "编号", value = "${item.taskNo}")
                                Metric(
                                    label = "内容类型",
                                    value = TodoContentType.labelOf(item.contentType),
                                )
                            }

                            // 耗时：预计与实际并排
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                Metric(
                                    label = "预计耗时",
                                    value = item.minuteEstimate?.let { "$it 分钟" } ?: "未设置",
                                )
                                Metric(label = "实际耗时", value = "${item.minuteActual} 分钟")
                            }

                            // 优先级、重要性、紧急程度：同一行并列
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Metric(
                                    label = "优先级",
                                    value = "${item.taskPriority}",
                                    valueColor = MaterialTheme.colorScheme.primary,
                                )
                                Metric(
                                    label = "重要性",
                                    value = "${item.levelImportance ?: "未设置"}",
                                )
                                Metric(
                                    label = "紧急程度",
                                    value = "${item.levelUrgency ?: "未设置"}",
                                )
                            }

                            // 时间：开始与最近更新同一行（标题在前，省去年份）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                TimeInfoText(value = item.gmtCreate, prefix = "开始")
                                TimeInfoText(value = item.gmtUpdate, prefix = "更新")
                            }

                            // 完成进度：标签 + 进度条 + 百分比保持在同一行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "完成进度",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(
                                    progress = {
                                        item.percentCompletion.coerceIn(0, 100) / 100f
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${item.percentCompletion}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 指标「名称 + 值」：名称弱化，值加亮加粗，便于一眼扫到数值。 */
@Composable
private fun Metric(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Normal)) {
                append(label)
                append(" ")
            }
            withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.SemiBold)) {
                append(value)
            }
        },
        style = MaterialTheme.typography.labelMedium,
    )
}

/** 一条「标签 + 时间」信息，格式 `MM-dd HH:mm`；值缺失或格式不符时整条不展示。 */
@Composable
private fun TimeInfoText(value: String?, prefix: String) {
    val text = TodoTime.formatLocalMonthDay(value)
    if (text.isNotBlank()) {
        Metric(label = prefix, value = text)
    }
}

/** 待办页的标签页，分别对应服务端的 filter 取值。 */
private enum class TodoTab(val label: String, val filter: Int) {
    PENDING("待开始", TodoApi.FILTER_PENDING),
    DOING("进行中", TodoApi.FILTER_DOING),
    TIMEOUT("已超时", TodoApi.FILTER_TIMEOUT),
    OVERDUE("已逾期", TodoApi.FILTER_OVERDUE),
}

private val CARD_SHAPE = RoundedCornerShape(14.dp)

// 完成状态的强调色：鲜明黄色填充 + 深色勾号，保证浅色/深色主题下都能看清
private val DONE_ACCENT = Color(0xFFFFC107)
private val DONE_CHECKMARK = Color(0xFF3E2723)
private const val REVEAL_WIDTH_DP = 88

// 完成动画时间线：勾选 → 划线 → 折叠消失
private const val STRIKE_DELAY_MS = 120
private const val STRIKE_ANIM_MS = 360
private const val COMPLETE_ANIM_MS = STRIKE_DELAY_MS + STRIKE_ANIM_MS + 120L
private const val REMOVE_ANIM_MS = 300
private const val EXPAND_ANIM_MS = 220
