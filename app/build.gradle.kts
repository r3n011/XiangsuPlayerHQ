import java.text.SimpleDateFormat
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

val abiSplitsRequested = providers.gradleProperty("pixelplay.enableAbiSplits")
    .getOrElse("true")
    .toBoolean()
// 只在 Release / Benchmark 构建时启用 ABI 分包
// 使用任务名判断，或通过 pixelplay.forceAbiSplits 强制启用
val forceAbiSplits = providers.gradleProperty("pixelplay.forceAbiSplits")
    .getOrElse("false")
    .toBoolean()
val isReleaseBuild = gradle.startParameter.taskNames.any { 
    it.contains("Release", ignoreCase = true) || it.contains("Benchmark", ignoreCase = true) 
}
val enableAbiSplits = abiSplitsRequested && (isReleaseBuild || forceAbiSplits)

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
        versionCode = 30
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"



        resConfigs("zh-rCN", "en")

        val telegramApiId = localProperties.getProperty("TELEGRAM_API_ID")?.ifEmpty { null }
            ?: "2040"
        val telegramApiHash = localProperties.getProperty("TELEGRAM_API_HASH")?.ifEmpty { null }
            ?: "b18441a1ff607e10a989891a5462e627"
        val githubToken = localProperties.getProperty("github.token")?.ifEmpty { null } ?: ""
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
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

    splits {
        abi {
            isEnable = enableAbiSplits
            reset()
            if (enableAbiSplits) {
                // ABI 分包：为不同架构生成独立 APK
                // arm64-v8a: 64位架构（主流设备）
                // armeabi-v7a: 32位架构（旧设备）
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = false
            }
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
            val currentName = output.outputFileName.toString()
            val abi = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").find { currentName.contains(it) }
            val abiSuffix = abi?.let {
                "-" + it.replace("arm64-v8a", "arm64").replace("armeabi-v7a", "arm32")
            } ?: ""

            output.outputFileName = "PixelPlay-${vName}-${vCode}-${date}-${variantName}${abiSuffix}.apk"

            // 设置 APK 输出目录为 G:\apk
            // 使用绝对路径，确保构建时能正确找到输出目录
            output.outputDirectory.set(providers.gradleProperty("pixelplay.outputDir").getOrElse("G:/apk"))
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

    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
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