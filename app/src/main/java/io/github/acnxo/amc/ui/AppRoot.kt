package io.github.acnxo.amc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.acnxo.amc.R
import io.github.acnxo.amc.ui.nav.DrawerHeader
import io.github.acnxo.amc.ui.nav.MenuDestination
import io.github.acnxo.amc.ui.nav.MenuNode
import io.github.acnxo.amc.ui.nav.MenuRepository
import io.github.acnxo.amc.ui.nav.MenuTreeNode
import io.github.acnxo.amc.ui.nav.findLeaf
import io.github.acnxo.amc.ui.nav.findPath
import io.github.acnxo.amc.ui.nav.firstLeaf
import io.github.acnxo.amc.ui.settings.ServerNodeScreen
import io.github.acnxo.amc.ui.version.VersionInfoScreen
import io.github.acnxo.amc.ui.web.WebContentScreen
import io.github.acnxo.amc.ui.web.WebViewHandle
import kotlinx.coroutines.launch

/**
 * 应用主界面：左侧 Material 3 抽屉导航 + 右侧 WebView 内容区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    menus: List<MenuNode> = MenuRepository.defaultMenus,
) {
    val appName = stringResource(R.string.app_name)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val webViewHandle = remember { WebViewHandle() }

    // 当前选中的叶子菜单（默认选中第一个叶子菜单：首页）
    var selectedId by rememberSaveable {
        mutableStateOf<String?>(menus.firstLeaf()?.id)
    }

    // 展开的分组 id 集合；用 listSaver 保证进程重建后仍能恢复
    val expandedIdsState = rememberSaveable(
        saver = listSaver<MutableState<Set<String>>, String>(
            save = { it.value.toList() },
            restore = { mutableStateOf(it.toSet()) },
        ),
    ) {
        mutableStateOf(setOf(MenuRepository.DEFAULT_EXPANDED_ID))
    }

    val selectedNode = remember(menus, selectedId) { menus.findLeaf(selectedId) }
    val path = remember(menus, selectedId) { menus.findPath(selectedId) }
    val title = path.joinToString(separator = " / ") { it.title }.ifEmpty { appName }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(
                    appName = appName,
                    subtitle = "应用导航",
                )
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    menus.forEach { node ->
                        MenuTreeNode(
                            node = node,
                            depth = 0,
                            selectedId = selectedId,
                            expandedIds = expandedIdsState.value,
                            onToggle = { id ->
                                val current = expandedIdsState.value
                                expandedIdsState.value = if (id in current) {
                                    current - id
                                } else {
                                    current + id
                                }
                            },
                            onSelect = { selected ->
                                selectedId = selected.id
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "打开菜单",
                            )
                        }
                    },
                    actions = {
                        if (selectedNode?.destination is MenuDestination.Web) {
                            IconButton(onClick = { webViewHandle.reload() }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "刷新",
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (val destination = selectedNode?.destination) {
                    is MenuDestination.Web -> WebContentScreen(
                        url = destination.url,
                        handle = webViewHandle,
                        modifier = Modifier.fillMaxSize(),
                    )

                    MenuDestination.VersionInfo -> VersionInfoScreen(
                        modifier = Modifier.fillMaxSize(),
                    )

                    MenuDestination.ServerConfig -> ServerNodeScreen(
                        modifier = Modifier.fillMaxSize(),
                    )

                    null -> EmptyPlaceholder(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "请从左侧菜单选择一个页面",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
