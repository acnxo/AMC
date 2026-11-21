// 顶层构建脚本：只声明插件，具体配置见各模块
// AGP 9 起内置 Kotlin 支持，不再声明 org.jetbrains.kotlin.android 插件。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
