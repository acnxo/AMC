import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// 版本号规则
//
//   基础版本：version.properties 中的 major/minor/patch
//   测试版（push 到 dev）：x.y.z.PPPPP，PPPPP 为 PR 编号，左补 0 到五位
//   正式版（push 到 main）：x.y.z
//
//   versionCode 由 CI 传入的 -PbuildNumber 决定（release 工作流的 run number），
//   在测试版与正式版之间保持全局单调递增，避免安装时被判定为降级。
// ---------------------------------------------------------------------------
val versionProperties = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

val baseMajor = versionProperties.getProperty("version.major", "1").trim().toIntOrNull() ?: 1
val baseMinor = versionProperties.getProperty("version.minor", "0").trim().toIntOrNull() ?: 0
val basePatch = versionProperties.getProperty("version.patch", "0").trim().toIntOrNull() ?: 0

val prNumber = providers.gradleProperty("prNumber")
    .orNull
    ?.toIntOrNull()
    ?.takeIf { it > 0 }

val buildNumber = providers.gradleProperty("buildNumber")
    .orNull
    ?.toIntOrNull()
    ?.takeIf { it > 0 }

// 快照包时间戳（yyMMddHHmm），传入后版本形如 x.y.z.PPPPP.yyMMddHHmm
val snapshotStamp = providers.gradleProperty("snapshotStamp")
    .orNull
    ?.takeIf { it.isNotBlank() }

val appVersionName = when {
    snapshotStamp != null -> "%d.%d.%d.%05d.%s".format(
        baseMajor,
        baseMinor,
        basePatch,
        prNumber ?: 0,
        snapshotStamp,
    )

    prNumber != null -> "%d.%d.%d.%05d".format(baseMajor, baseMinor, basePatch, prNumber)
    else -> "%d.%d.%d".format(baseMajor, baseMinor, basePatch)
}

val appVersionCode = when {
    buildNumber != null -> (1_000_000 + buildNumber).coerceAtMost(2_000_000_000)
    prNumber != null -> (1_000_000 + prNumber).coerceAtMost(2_000_000_000)
    else -> baseMajor * 1_000_000 + baseMinor * 10_000 + basePatch * 100
}

// 正式签名证书由 CI 通过 -P / ORG_GRADLE_PROJECT_* 注入；未提供时回退到 debug 证书。
val releaseKeystorePath = providers.gradleProperty("releaseKeystorePath")
    .orNull
    ?.takeIf { it.isNotBlank() }

android {
    namespace = "io.github.acnxo.amc"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.acnxo.amc"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.gradleProperty("releaseKeystorePassword").orNull
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 暂时不开启混淆，保持与 debug 相近的行为
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 配置了正式证书就用正式证书；否则回退到 debug 证书，
            // 保证发布出来的 APK / AAB 始终可直接安装测试。
            signingConfig = if (releaseKeystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    debugImplementation(libs.androidx.ui.tooling)
}
