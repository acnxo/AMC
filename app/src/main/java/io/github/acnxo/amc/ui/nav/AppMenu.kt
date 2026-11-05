package io.github.acnxo.amc.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 抽屉顶部品牌区。 */
@Composable
fun DrawerHeader(
    appName: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(
            text = appName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 递归渲染一个菜单节点。
 *
 * - 分组节点：点击切换展开/折叠，右侧显示箭头；
 * - 叶子节点：点击后选中并触发 [onSelect]；
 * - 每深一级向右缩进，视觉上区分层级。
 */
@Composable
fun MenuTreeNode(
    node: MenuNode,
    depth: Int,
    selectedId: String?,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelect: (MenuNode) -> Unit,
) {
    val indent = Modifier.padding(start = (depth * 12).dp)
    val nodeIcon = node.icon
    val iconContent: (@Composable () -> Unit)? = if (nodeIcon != null) {
        { Icon(imageVector = nodeIcon, contentDescription = null) }
    } else {
        null
    }

    if (node.isLeaf) {
        NavigationDrawerItem(
            modifier = indent,
            label = { Text(text = node.title, maxLines = 1) },
            selected = node.id == selectedId,
            onClick = { onSelect(node) },
            icon = iconContent,
        )
    } else {
        val expanded = node.id in expandedIds
        Column {
            NavigationDrawerItem(
                modifier = indent,
                label = { Text(text = node.title, maxLines = 1) },
                selected = false,
                onClick = { onToggle(node.id) },
                icon = iconContent,
                badge = {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowDown
                        } else {
                            Icons.Filled.KeyboardArrowRight
                        },
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    node.children.forEach { child ->
                        MenuTreeNode(
                            node = child,
                            depth = depth + 1,
                            selectedId = selectedId,
                            expandedIds = expandedIds,
                            onToggle = onToggle,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}
