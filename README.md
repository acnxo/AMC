# AMC

基于 Kotlin + Jetpack Compose（Material Design 3）的 Android 客户端。

首屏为「左侧抽屉导航 + 右侧 WebView」结构：

- 抽屉菜单支持任意层级（内置三级菜单示例），分组节点可展开 / 折叠；
- 第一个菜单为 **首页**（叶子节点），选中后在 App 内置 WebView 打开 <https://acanx.com>；
- 主题遵循 Material 3，Android 12+ 自动跟随系统动态取色，支持深色模式。

> 本项目约定**不在本地编译**，所有构建都通过 GitHub Actions 完成。

## 目录结构

```
.
├── .github/workflows/android-build.yml   # CI：构建 debug APK 并上传产物
├── gradle/libs.versions.toml             # 依赖版本目录
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/io/github/acnxo/amc/
│       │   ├── MainActivity.kt           # 入口 Activity
│       │   └── ui/
│       │       ├── AppRoot.kt            # 抽屉 + 顶栏 + 内容区
│       │       ├── nav/                  # 菜单模型、数据源、递归渲染组件
│       │       ├── theme/                # Material 3 配色与主题
│       │       └── web/                  # WebView 封装
│       └── res/                          # 图标、字符串、主题
└── settings.gradle.kts
```

## 菜单如何扩展

菜单数据集中在 `app/src/main/java/io/github/acnxo/amc/ui/nav/MenuRepository.kt`：

```kotlin
MenuNode(
    id = "products",              // 唯一 id
    title = "产品中心",            // 显示文本
    icon = Icons.Filled.ShoppingCart,
    children = listOf(            // 有 children 即为可折叠分组
        MenuNode(
            id = "products.mobile",
            title = "移动应用",
            children = listOf(    // 支持三级及更深层级
                MenuNode(id = "products.mobile.amc", title = "AMC", url = "https://acanx.com"),
            ),
        ),
    ),
)
```

- 分组节点：点击展开 / 折叠，状态会自动保存（屏幕旋转、进程重建后恢复）；
- 叶子节点：`url` 非空时在 WebView 中打开；为空时展示占位提示；
- 顶栏标题显示从根节点到当前节点的完整路径。

后续如需服务端下发菜单，只要把 `MenuRepository.defaultMenus` 换成动态解析出的
`List<MenuNode>`，界面层无需改动。

## GitHub Actions 构建

工作流：`.github/workflows/android-build.yml`

触发条件：

- push 到 `main`
- 向 `main` 发起 Pull Request
- 手动 `workflow_dispatch`

构建产物：在 Actions 运行详情页的 **Artifacts** 中下载 `amc-debug-apk`。

本地**不执行** Gradle 构建；如果需要本地查看代码，仅用 IDE 打开即可。

## 许可证

[GNU General Public License v3.0](LICENSE)
