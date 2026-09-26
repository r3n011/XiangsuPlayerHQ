import com.android.build.api.variant.FilterConfiguration
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.parcelize)
}

// Load keystore properties early to avoid unresolved references inside the android block
val keystoreProperties = Properties().apply {
    val propFile = rootProject.file("keystore.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

val localProperties = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

/**
 * 对敏感字符串做 XOR + Base64 混淆，避免明文常量进入 BuildConfig / DEX 字符串常量池。
 *
 * 注意：这是「混淆」而非「加密」——密钥随包内置，只能提高提取门槛（挡住 `strings` 之类的
 * 静态提取），无法抵御针对 APK 的逆向分析。运行时由 GitHubToken 用同一密钥解码还原。
 */
fun obfuscateSecret(raw: String, key: String = "pixelplay-gh-token-v1"): String {
    if (raw.isEmpty()) return ""
    val rawBytes = raw.toByteArray(Charsets.UTF_8)
    val keyBytes = key.toByteArray(Charsets.UTF_8)
    val xored = ByteArray(rawBytes.size) { i ->
        (rawBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
    }
    return Base64.getEncoder().encodeToString(xored)
}

// 是否启用 ABI 分包：仅 Release/Benchmark 构建时打 x86 + arm64-v8a 两个 split APK，
// Debug 构建生成通用 APK（含两 ABI）以支持 x86/x86_64 模拟器直接安装
val isAbiSplitEnabled = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true) ||
        taskName.contains("Benchmark", ignoreCase = true)
}

val enableComposeCompilerReports = providers.gradleProperty("pixelplay.enableComposeCompilerReports")
    .getOrElse("false")
    .toBoolean()

@Suppress("DEPRECATION")
android {
    namespace = "com.theveloper.pixelplay"
    compileSdk = 37

    sourceSets {
        getByName("androidTest") {
            assets.directories.add(file("$projectDir/schemas").path)
        }
    }

    androidResources {
        noCompress.add("tflite")
        // autoeq_profiles.json 约 1.5MB > 1MB 阈值：不压缩直接存入 APK，
        // 避免 release 构建下压缩 asset 读取失败导致 AutoEQ 数据库丢失
        noCompress.add("json")
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "/META-INF/io.netty.versions.properties",
                "META-INF/CONTRIBUTORS.md",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/rxjava.properties",
                "META-INF/services/javax.annotation.processing.Processor",
                "*.proto",
                "*.yaml",
                "*.yml",
                "LICENSE",
                "NOTICE",
                "CHANGELOG",
                "README",
                "*.txt",
                "*.md",
                "*.html",
                "*.css"
            )
            pickFirsts += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt"
            )
        }
    }

    defaultConfig {
        applicationId = "com.r3n011.pixelplay"
        minSdk = 23
        targetSdk = 36
        multiDexEnabled = true
        versionCode = 59
        versionName = "1.6.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Debug 通用包用 abiFilters 限定 arm64-v8a + x86（含预编译 libusb 的 arm64，
            // x86 自动跳过 USB 独占输出，见 CMakeLists.txt）；
            // Release/Benchmark 分包时由 splits 控制 ABI，不可再设 abiFilters（二者冲突）
            if (!isAbiSplitEnabled) {
                abiFilters += setOf("arm64-v8a", "x86")
            }
        }


        resConfigs(
            "en", "zh-rCN",
            "de", "es", "fr", "in", "it", "ko", "nb", "ru", "tr"
        )

        val telegramApiId = localProperties.getProperty("TELEGRAM_API_ID")?.ifEmpty { null }
            ?: "2040"
        val telegramApiHash = localProperties.getProperty("TELEGRAM_API_HASH")?.ifEmpty { null }
            ?: "b18441a1ff607e10a989891a5462e627"
        val githubToken = localProperties.getProperty("github.token")?.ifEmpty { null } ?: ""
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
        // GitHub PAT 不以明文写入 BuildConfig：构建期先 XOR + Base64 混淆再注入，
        // 运行时由 com.theveloper.pixelplay.data.github.GitHubToken 解码，
        // 避免通过 strings / DEX 字符串常量池直接提取到明文 token。
        buildConfigField("String", "GITHUB_TOKEN_OBF", "\"${obfuscateSecret(githubToken)}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("$rootDir/vz-pixelplay.jks")
            storePassword = keystoreProperties.getProperty("storePassword") ?: "dummyPassword"
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: "dummyAlias"
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: "dummyPassword"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            val keystoreFile = file("$rootDir/vz-pixelplay.jks")
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("$projectDir/src/main/cpp/CMakeLists.txt")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.useJUnitPlatform() }
    }

    lint {
        checkReleaseBuilds = false
    }

    // ABI 分包：Release/Benchmark 产出 x86 与 arm64-v8a 两个 split APK；
    // Debug（isEnable=false）产出通用 APK
    splits {
        abi {
            isEnable = isAbiSplitEnabled
            reset()
            include("arm64-v8a", "x86")
            isUniversalApk = false
        }
    }

    bundle {
        abi.enableSplit = true
        density.enableSplit = true
        language.enableSplit = true
    }
}

androidComponents {
    onVariants { variant ->
        val vName = android.defaultConfig.versionName ?: "unknown"
        val vCode = android.defaultConfig.versionCode ?: 0
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val variantName = variant.name

        variant.outputs.forEach { output ->
            // 从 output 的过滤器读取 ABI（AGP 9 分包时默认文件名不含 ABI，不能靠文件名猜测）。
            // AGP 9 已移除 OutputFilter，改用 FilterConfiguration（filterType + identifier）。
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val abiSuffix = abi?.let {
                "-" + it.replace("arm64-v8a", "arm64").replace("armeabi-v7a", "arm32")
            } ?: ""

            output.outputFileName = "PixelPlay-${vName}-${vCode}-${date}-${variantName}${abiSuffix}.apk"
        }
    }
}

composeCompiler {
    // StrongSkipping is now enabled by default.
}

baselineProfile {
    // Keep release builds fast to invoke locally, but make generated profiles usable as
    // startup dex-layout input once they are checked into the app.
    automaticGenerationDuringBuild = false
    saveInSrc = true
    dexLayoutOptimization = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Core & Optimization
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // Parcelize runtime
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.3.0")
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.lifecycleprocess)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)

    // Haze blur effect
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // DI & Navigation
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.ktx)

    // Storage & Paging
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.common)

    // Media & Files
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.ffmpeg)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.midi)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.media)
    implementation(libs.coil.compose)
    implementation(libs.taglib)
    implementation(libs.jaudiotagger)
    implementation(libs.vorbisjava.core)
    implementation(libs.wavy.slider)
    implementation(libs.androidx.graphics.shapes)

    // Markdown 渲染（关于页更新日志等）
    implementation(libs.markwon)
    implementation(libs.markwon.image)

    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.zxing.core)
    implementation(libs.gson)
    implementation(libs.quickjs.wrapper.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.0")
    implementation("io.ktor:ktor-server-cors-jvm:3.5.0")
    implementation("io.ktor:ktor-server-default-headers-jvm:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.0")

    // Identity & Background
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.security.crypto)
    implementation(libs.google.play.services.cast.framework)
    implementation(libs.tdlib)

    // UI Utilities & Extra
    implementation(libs.timber)
    implementation(libs.generativeai)
    implementation(libs.smooth.corner.rect.android.compose)
    implementation(libs.reorderables)
    implementation(libs.codeview)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.pinyin4j.core)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.accompanist.permissions)
    implementation(libs.capturable) {
        exclude(group = "androidx.compose.animation")
        exclude(group = "androidx.compose.foundation")
        exclude(group = "androidx.compose.runtime")
        exclude(group = "androidx.compose.ui")
    }

    // 友盟移动统计 U-App
    // asms 是友盟全部业务 SDK 共用的公共基础组件，整个工程只声明一次（新增其它友盟产品时无需重复添加）
    implementation(libs.umeng.asms)
    // 统计业务 SDK：提供 UMConfigure 初始化与 MobclickAgent 埋点能力
    implementation(libs.umeng.common)

    // Nothing Glyph Matrix SDK
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Projects
    implementation(project(":shared"))

    // Testing (Unit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junitplatformlauncher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(kotlin("test"))

    // Testing (Instrumentation)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.worktesting)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.benchmark.macro.junit4)
    androidTestImplementation(libs.androidx.uiautomator)

    // Debug
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    constraints {
        implementation(libs.netty.common)
        implementation(libs.netty.handler)
        implementation(libs.netty.codec.http)
        implementation(libs.netty.codec.http2)
        implementation(libs.bouncycastle.bcprov)
        implementation(libs.bouncycastle.bcpkix)
        implementation(libs.commons.lang3)
        implementation(libs.jdom2)
        implementation(libs.jose4j)
        implementation(libs.apache.httpclient)

        implementation("androidx.compose.foundation:foundation:1.12.0-alpha03")
        implementation("androidx.compose.ui:ui:1.12.0-alpha03")
        implementation("androidx.compose.ui:ui-graphics:1.12.0-alpha03")
        implementation("androidx.compose.ui:ui-tooling:1.12.0-alpha03")
        implementation("androidx.compose.ui:ui-tooling-preview:1.12.0-alpha03")
        implementation("androidx.compose.ui:ui-text-google-fonts:1.12.0-alpha03")
        implementation("androidx.compose.animation:animation:1.12.0-alpha03")
    }
}

configurations.all {
    resolutionStrategy.force(
        "androidx.compose.foundation:foundation:1.12.0-alpha03",
        "androidx.compose.foundation:foundation-layout:1.12.0-alpha03",
        "androidx.compose.foundation:foundation-android:1.12.0-alpha03",
        "androidx.compose.foundation:foundation-layout-android:1.12.0-alpha03"
    )
}



tasks.withType<Test> {
    useJUnitPlatform()
}