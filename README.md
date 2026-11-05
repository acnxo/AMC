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
├── .github/
│   ├── dependabot.yml                    # 每周自动更新 Gradle 依赖与 Actions 版本
│   └── workflows/
│       ├── snapshot-build.yml            # 特性分支快照构建（feat/*、fix/*）
│       └── release.yml                   # dev → 测试版，main → 正式版
├── version.properties                    # 基础版本号 x.y.z
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

## 分支与发布流程

```
feat/* fix/*  --push / PR 打开或更新-->  快照版 Release
                                        x.y.z.PPPPP.yyMMddHHmm
        │
        └──PR-->  dev  --PR-->  main
                    │             │
              测试版 Release   正式版 Release
          x.y.z.PPPPP（测试）    x.y.z（正式）
```

### 版本号规则

基础版本写在 `version.properties`：

```properties
version.major=1
version.minor=0
version.patch=0
```

| 触发 | 版本名 | Tag | Release 类型 | 产物 |
| --- | --- | --- | --- | --- |
| 向 `feat/*` / `fix/*` 递交代码，或这些分支的 PR 打开 / 更新 | `x.y.z.PPPPP.yyMMddHHmm` | `vx.y.z.PPPPP.yyMMddHHmm` | pre-release（快照） | debug APK + release APK |
| PR 合并到 `dev` | `x.y.z.PPPPP`（PR 号左补 0 到五位） | `vx.y.z.PPPPP` | pre-release | debug APK + release APK + AAB |
| `dev` 合并到 `main` | `x.y.z` | `vx.y.z` | 正式版（latest） | release APK + AAB |

- `PPPPP` 为关联 PR 编号（五位，左补 0），无 PR 时为 `00000`；
- `yyMMddHHmm` 为 Asia/Shanghai 构建时间戳，仅快照包使用，保证同一 PR 多次递交的唯一性；
- 快照包的版本名与 tag 与代码提交一一对应，不会覆盖已有 tag（同名则仅保留 Actions 产物）；
- 正式版发布前请先更新 `version.properties`，否则 `vx.y.z` tag 已存在会跳过发布并给出提示；
- `versionCode` 由各工作流的 run number 生成（`1_000_000 + run*10 + attempt`）。

### 正式签名（可选）

正式版优先使用仓库 Secrets 中的签名证书，未配置时回退到 debug 证书（仍可安装，但不能上架）：

| Secret | 说明 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | keystore 文件的 base64 内容 |
| `RELEASE_KEYSTORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key 密码 |

生成 base64：`base64 -w0 release.jks`。

## GitHub Actions

### 快照构建（snapshot-build.yml）

- 触发：
  - push 到 `feat/**` 或 `fix/**` 分支；
  - 这些分支向 `dev` / `main` 发起 PR（opened / synchronize / reopened）；
  - 手动 `workflow_dispatch`；
- 动作：构建 debug APK + release APK（debug 签名），上传 Actions **Artifacts**，并创建 **pre-release** 快照包（tag 为 `vx.y.z.PPPPP.yyMMddHHmm`）；
- 来自 fork 的 PR 只上传 Artifacts，不创建 Release。

### 发布（release.yml）

- 触发：push 到 `dev` 或 `main`，或手动 `workflow_dispatch`
- 动作：构建 debug / release APK 与 AAB，打 tag，创建 GitHub Release 并上传全部产物及 `SHA256SUMS.txt`
- 测试版标记为 **pre-release**，正式版标记为 **latest**，均可直接在 Release 页面下载安装

本地**不执行** Gradle 构建；如果需要本地查看代码，仅用 IDE 打开即可。

## 依赖自动更新（Dependabot）

`.github/dependabot.yml` 配置了两类每周检查（周一 06:00 Asia/Shanghai）：

- `gradle`：更新 `gradle/libs.versions.toml` 中的依赖版本；
- `github-actions`：更新工作流中 `uses:` 的 action 版本；

Dependabot 提交的 PR 同样会走 PR 验证，合并后自动进入对应发布通道。

## 许可证

[GNU General Public License v3.0](LICENSE)
