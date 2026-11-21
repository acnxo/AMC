package io.github.acnxo.amc.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** APK 下载目录与系统安装器的桥接。 */
object ApkInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 是否已被允许安装未知来源应用（Android 8.0+ 需要单独授权）。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳转到本应用的「安装未知应用」授权页。 */
    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 通过 FileProvider 把已下载的 APK 交给系统安装器。 */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 更新包的存放目录：外部私有目录，可由 FileProvider 共享给安装器。 */
    fun downloadDir(context: Context): File =
        context.getExternalFilesDir("updates") ?: File(context.filesDir, "updates")
}
