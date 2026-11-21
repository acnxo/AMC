package io.github.acnxo.amc.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import io.github.acnxo.amc.data.ServerKind

/**
 * 菜单数据源。
 *
 * 目前是写死的示例数据，后续如果需要动态下发（服务端配置 / 本地数据库），
 * 只要把 [defaultMenus] 换成从数据源解析出来的 List<MenuNode> 即可，
 * 界面层完全不用改。
 *
 * 约定：每个节点（无论分组还是叶子、无论第几级）都带 [MenuNode.icon]，
 * 抽屉里统一在文字左侧展示，保证各层级的视觉结构一致。
 */
object MenuRepository {

    /** 首页地址，也是各个示例叶子菜单的默认地址。 */
    const val HOME_URL = "https://acanx.com"

    /** 默认展开的分组（产品中心），方便首次进入时看到三级菜单。 */
    const val DEFAULT_EXPANDED_ID = "products"

    val defaultMenus: List<MenuNode> = listOf(
        MenuNode(
            id = "home",
            title = "首页",
            icon = Icons.Filled.Home,
            destination = MenuDestination.Web(HOME_URL),
        ),
        MenuNode(
            id = "products",
            title = "产品中心",
            icon = Icons.Filled.ShoppingCart,
            children = listOf(
                MenuNode(
                    id = "products.mobile",
                    title = "移动应用",
                    icon = Icons.Filled.Smartphone,
                    children = listOf(
                        MenuNode(
                            id = "products.mobile.amc",
                            title = "AMC",
                            icon = Icons.Filled.Android,
                            destination = MenuDestination.Web(HOME_URL),
                        ),
                        MenuNode(
                            id = "products.mobile.aha",
                            title = "AHA",
                            icon = Icons.Filled.Apps,
                            destination = MenuDestination.Web(HOME_URL),
                        ),
                    ),
                ),
                MenuNode(
                    id = "products.content",
                    title = "内容管理",
                    icon = Icons.Filled.Folder,
                    children = listOf(
                        MenuNode(
                            id = "products.content.todo",
                            title = "待办",
                            icon = Icons.Filled.Checklist,
                            children = listOf(
                                MenuNode(
                                    id = "products.content.todo.pending",
                                    title = "待办事项",
                                    icon = Icons.Filled.PendingActions,
                                    destination = MenuDestination.Placeholder,
                                ),
                                MenuNode(
                                    id = "products.content.todo.done",
                                    title = "已完成事项",
                                    icon = Icons.Filled.CheckCircle,
                                    destination = MenuDestination.Placeholder,
                                ),
                                MenuNode(
                                    id = "products.content.todo.stats",
                                    title = "统计分析",
                                    icon = Icons.Filled.BarChart,
                                    destination = MenuDestination.Placeholder,
                                ),
                            ),
                        ),
                    ),
                ),
                MenuNode(
                    id = "products.desktop",
                    title = "桌面工具",
                    icon = Icons.Filled.Computer,
                    destination = MenuDestination.Web(HOME_URL),
                ),
            ),
        ),
        MenuNode(
            id = "settings",
            title = "设置",
            icon = Icons.Filled.Settings,
            children = listOf(
                MenuNode(
                    id = "settings.service",
                    title = "服务",
                    icon = Icons.Filled.Cloud,
                    children = listOf(
                        MenuNode(
                            id = "settings.service.cms",
                            title = "内容服务节点",
                            icon = Icons.Filled.Dns,
                            destination = MenuDestination.ServerConfig(ServerKind.CMS),
                        ),
                        MenuNode(
                            id = "settings.service.finv",
                            title = "Finv服务节点",
                            icon = Icons.Filled.Storage,
                            destination = MenuDestination.ServerConfig(ServerKind.FINV),
                        ),
                    ),
                ),
            ),
        ),
        MenuNode(
            id = "about",
            title = "关于",
            icon = Icons.Filled.Info,
            children = listOf(
                MenuNode(
                    id = "about.intro",
                    title = "公司介绍",
                    icon = Icons.Filled.Business,
                    destination = MenuDestination.Web(HOME_URL),
                ),
                MenuNode(
                    id = "about.contact",
                    title = "联系我们",
                    icon = Icons.Filled.Email,
                    destination = MenuDestination.Web(HOME_URL),
                ),
                MenuNode(
                    id = "about.update",
                    title = "版本更新",
                    icon = Icons.Filled.SystemUpdate,
                    destination = MenuDestination.Update,
                ),
                MenuNode(
                    id = "about.version",
                    title = "版本信息",
                    icon = Icons.Filled.NewReleases,
                    destination = MenuDestination.VersionInfo,
                ),
            ),
        ),
    )
}
