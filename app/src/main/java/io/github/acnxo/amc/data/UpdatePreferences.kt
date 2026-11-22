package io.github.acnxo.amc.data

import android.content.Context

/**
 * 「版本更新」页的本地偏好。
 *
 * 记住用户上次选择的通道与包类型，再次进入页面时沿用上次选择，
 * 而不是每次都回落到「正式版 / APK」。
 */
object UpdatePreferences {

    private const val PREFS_NAME = "amc_update_prefs"
    private const val KEY_CHANNEL = "selected_channel"
    private const val KEY_PACKAGE = "selected_package"

    fun channel(context: Context): UpdateChannel = prefs(context)
        .getString(KEY_CHANNEL, null)
        ?.let { name -> runCatching { UpdateChannel.valueOf(name) }.getOrNull() }
        ?: UpdateChannel.STABLE

    fun setChannel(context: Context, channel: UpdateChannel) {
        prefs(context).edit().putString(KEY_CHANNEL, channel.name).apply()
    }

    fun packageType(context: Context): PackageType = prefs(context)
        .getString(KEY_PACKAGE, null)
        ?.let { name -> runCatching { PackageType.valueOf(name) }.getOrNull() }
        ?: PackageType.APK

    fun setPackageType(context: Context, type: PackageType) {
        prefs(context).edit().putString(KEY_PACKAGE, type.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
