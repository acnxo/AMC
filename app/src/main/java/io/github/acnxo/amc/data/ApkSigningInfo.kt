package io.github.acnxo.amc.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 当前已安装 APK 的签名证书信息。
 *
 * 用于在设备上直接核对安装包签名，判断不同构建之间是否发生了签名变化
 * （签名不一致时 Android 会拒绝覆盖安装）。
 */
data class ApkSigningInfo(
    /** 证书主体，例如 `CN=ACANX, OU=AMC, O=ACANX, ...` */
    val subject: String,
    /** 大写、冒号分隔的 SHA-256 指纹，例如 `A3:65:99:3C:...` */
    val sha256: String,
)

/** 读取当前运行 APK 的签名证书：API 28+ 使用 SigningInfo，低版本回退到 signatures。 */
fun readApkSigningInfo(context: Context): ApkSigningInfo? = runCatching {
    val packageManager = context.packageManager
    val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.apkContentsSigners
            ?.firstOrNull()
    } else {
        @Suppress("DEPRECATION")
        packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            .signatures
            ?.firstOrNull()
    } ?: return null

    val certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(signature.toByteArray())) as X509Certificate
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)

    ApkSigningInfo(
        subject = certificate.subjectX500Principal.name,
        sha256 = digest.joinToString(separator = ":") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        },
    )
}.getOrNull()
