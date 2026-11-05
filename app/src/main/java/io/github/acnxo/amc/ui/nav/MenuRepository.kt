package io.github.acnxo.amc.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart

/**
 * 菜单数据源。
 *
 * 目前是写死的示例数据，后续如果需要动态下发（服务端配置 / 本地数据库），
 * 只要把 [defaultMenus] 换成从数据源解析出来的 List<MenuNode> 即可，
 * 界面层完全不用改。
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
            url = HOME_URL,
            icon = Icons.Filled.Home,
        ),
        MenuNode(
            id = "products",
            title = "产品中心",
            icon = Icons.Filled.ShoppingCart,
            children = listOf(
                MenuNode(
                    id = "products.mobile",
                    title = "移动应用",
                    children = listOf(
                        MenuNode(
                            id = "products.mobile.amc",
                            title = "AMC",
                            url = HOME_URL,
                        ),
                        MenuNode(
                            id = "products.mobile.aha",
                            title = "AHA",
                            url = HOME_URL,
                        ),
                    ),
                ),
                MenuNode(
                    id = "products.desktop",
                    title = "桌面工具",
                    url = HOME_URL,
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
                    url = HOME_URL,
                ),
                MenuNode(
                    id = "about.contact",
                    title = "联系我们",
                    url = HOME_URL,
                ),
            ),
        ),
    )
}
