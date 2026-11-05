package io.github.acnxo.amc.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 叶子菜单打开的内置页面类型。
 *
 * 分组节点（有 children）没有 destination。
 */
sealed interface MenuDestination {
    /** 在内置 WebView 中打开网页 */
    data class Web(val url: String) : MenuDestination

    /** 版本信息页 */
    data object VersionInfo : MenuDestination

    /** 服务器节点配置页 */
    data object ServerConfig : MenuDestination
}

/**
 * 导航菜单节点。
 *
 * - 有 [children] 的节点是可折叠的分组；
 * - 没有 [children] 的节点是叶子菜单，点击后按 [destination] 决定展示的内容。
 *
 * 层级不设限，递归渲染即可支持二级、三级乃至更多级菜单。
 */
data class MenuNode(
    val id: String,
    val title: String,
    val icon: ImageVector? = null,
    val destination: MenuDestination? = null,
    val children: List<MenuNode> = emptyList(),
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

/** 按 id 查找叶子节点（分组节点不会命中）。 */
fun List<MenuNode>.findLeaf(id: String?): MenuNode? {
    if (id == null) return null
    for (node in this) {
        if (node.id == id && node.isLeaf) return node
        node.children.findLeaf(id)?.let { return it }
    }
    return null
}

/** 按 id 查找从根到目标节点的完整路径，可用于面包屑标题。 */
fun List<MenuNode>.findPath(id: String?): List<MenuNode> {
    if (id == null) return emptyList()
    for (node in this) {
        if (node.id == id) return listOf(node)
        val childPath = node.children.findPath(id)
        if (childPath.isNotEmpty()) return listOf(node) + childPath
    }
    return emptyList()
}

/** 第一棵子树中遇到的第一叶子节点，用于确定默认选中项。 */
fun List<MenuNode>.firstLeaf(): MenuNode? {
    for (node in this) {
        if (node.isLeaf) return node
        node.children.firstLeaf()?.let { return it }
    }
    return null
}
