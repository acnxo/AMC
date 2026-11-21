package io.github.acnxo.amc.data

/** 版本更新通道。 */
enum class UpdateChannel(val label: String, val hint: String) {
    STABLE("正式版", "main 分支发布，稳定可用"),
    BUILD("构建版", "dev 分支发布，跟随最新改动"),
    SNAPSHOT("快照版", "特性分支临时构建，仅供验证"),
}

/** 安装包类型。 */
enum class PackageType(val label: String) {
    APK("APK"),
    AAB("AAB"),
}

/** Release 中的一个下载资产。 */
data class ReleaseAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
)

/**
 * GitHub Release 的解析结果。
 *
 * 通道通过 tag 的段数判定，不依赖标题文案：
 * - `v1.0.0`                     → 正式版（3 段）
 * - `v1.0.0.00014`               → 测试版（4 段，末段为 PR 编号）
 * - `v1.0.0.00014.2611212349`    → 快照版（5 段，末段为时间戳）
 */
data class ReleaseInfo(
    val tag: String,
    val title: String,
    val channel: UpdateChannel,
    val versionName: String,
    val publishedAt: String,
    val notes: String,
    val assets: List<ReleaseAsset>,
) {
    /** 按包类型挑选一个可下载资产；APK 优先 release 构建，其次其它非 debug/普通 APK。 */
    fun assetFor(type: PackageType): ReleaseAsset? = when (type) {
        PackageType.APK -> assets.firstOrNull { it.name.endsWith("-release.apk") }
            ?: assets.firstOrNull {
                it.name.endsWith(".apk") && !it.name.contains("-debug")
            }
            ?: assets.firstOrNull { it.name.endsWith(".apk") }

        PackageType.AAB -> assets.firstOrNull { it.name.endsWith(".aab") }
    }

    companion object {
        /** 由 tag 判定通道；无法识别的 tag 返回 null。 */
        fun channelOf(tag: String): UpdateChannel? =
            when (tag.removePrefix("v").split(".").size) {
                3 -> UpdateChannel.STABLE
                4 -> UpdateChannel.BUILD
                in 5..8 -> UpdateChannel.SNAPSHOT
                else -> null
            }
    }
}
