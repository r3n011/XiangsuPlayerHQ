# =============================================================================
# PixelPlayer ProGuard Rules
# Version: 2.1
# Last Updated: 2026-07-11
# =============================================================================

# =============================================================================
# 一、核心语言和框架特性
# =============================================================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# 保留合成方法（用于反射）
-keepclassmembers class * {
    *** $defaultImpls;
}

# 保留 companion object
-keepclassmembers class ** {
    *** Companion;
}

# 保留枚举的 values 和 valueOf 方法
-keepclassmembers enum ** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable Creator
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留所有实现 Serializable 的类
-keep class * implements java.io.Serializable { *; }

# 保留所有 @Keep 注解的类和方法
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}

# =============================================================================
# 二、序列化支持
# =============================================================================

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** $serializer;
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# =============================================================================
# 三、第三方库规则
# =============================================================================

# AndroidX Core
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.constraintlayout.compose.** { *; }

# Media3
-keep class androidx.media3.** { *; }

# Network
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# DI
-keep class dagger.hilt.** { *; }
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keepclassmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <fields>;
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
}

# TDLib
-keep class org.drinkless.tdlib.** { *; }

# Ktor & Netty
-keep class io.netty.channel.socket.nio.NioServerSocketChannel { public <init>(); }
-keep class io.netty.channel.socket.nio.NioSocketChannel { public <init>(); }
-keep class io.netty.channel.epoll.EpollServerSocketChannel { public <init>(); }
-keep class io.netty.channel.epoll.EpollSocketChannel { public <init>(); }
-keep class io.netty.channel.kqueue.KQueueServerSocketChannel { public <init>(); }
-keep class io.netty.channel.kqueue.KQueueSocketChannel { public <init>(); }
-keep class io.ktor.server.engine.** { *; }
-keep class io.ktor.server.cio.** { *; }
-dontwarn io.ktor.**
-dontwarn io.netty.**

# TagLib / JAudioTagger
-keep class com.kyant.taglib.** { *; }
-dontwarn com.kyant.taglib.**
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ExoPlayer FFmpeg/MIDI
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.ffmpeg.** { *; }
-keep class androidx.media3.decoder.midi.** { *; }
-keep class com.jsyn.** { *; }
-keep class com.softsynth.** { *; }
-dontwarn com.jsyn.**
-dontwarn com.softsynth.**

# Kuromoji / Pinyin4J
-keep class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**
-keep class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**

# javax.* APIs
-keep class javax.lang.model.** { *; }
-keep class javax.sound.sampled.** { *; }
-keep class com.squareup.javapoet.** { *; }

# JSON.org
-keep class org.json.** { *; }
-dontwarn org.json.**

# SLF4J
-keep class org.slf4j.** { *; }

# =============================================================================
# 四、应用核心模块
# =============================================================================

# Database
-keep class com.theveloper.pixelplay.data.database.** { *; }
-keep class androidx.room.** { *; }

# Backup
-keep class com.theveloper.pixelplay.data.backup.** { *; }

# AI
-keep class com.theveloper.pixelplay.data.ai.** { *; }

# Lyrics
-keep class com.theveloper.pixelplay.data.repository.LyricsRepositoryImpl$LyricsData { *; }

# Preferences
-keep class com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry { *; }

# Telegram
-keep class com.theveloper.pixelplay.data.telegram.TelegramStreamProxy { *; }

# Cast
-keep class com.theveloper.pixelplay.data.service.cast.CastOptionsProvider { *; }
-keep class * implements com.google.android.gms.cast.framework.OptionsProvider

# Glance Widget
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# =============================================================================
# 五、Android组件
# =============================================================================

-keep class com.theveloper.pixelplay.PixelPlayApplication { *; }
-keep class com.theveloper.pixelplay.MainActivity { *; }
-keep class com.theveloper.pixelplay.SplashActivity { *; }

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers enum ** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclasseswithmembernames class * {
    native <methods>;
}

# =============================================================================
# 六、关键库保护规则
# =============================================================================

# QuickJS JS引擎
-keep class com.whitestein.jq.** { *; }
-dontwarn com.whitestein.jq.**
-keep class org.quickjs.** { *; }
-dontwarn org.quickjs.**

# Room 数据库
-keep class androidx.room.** { *; }
-keep class com.theveloper.pixelplay.data.database.** { *; }
-keep class com.theveloper.pixelplay.data.database.entities.** { *; }
-keep class com.theveloper.pixelplay.data.database.daos.** { *; }
-keep class com.theveloper.pixelplay.data.database.migrations.** { *; }
-keepclassmembers class com.theveloper.pixelplay.data.database.** {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# Hilt 注入
-keep class dagger.hilt.android.lifecycle.ViewModelInject { *; }
-keep class dagger.hilt.android.AndroidEntryPoint { *; }
-keep class **_HiltModules { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keepclassmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <init>();
    @dagger.hilt.android.AndroidEntryPoint class <inner-classes>;
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
    @javax.inject.Singleton <fields>;
}

# Compose 关键类
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.constraintlayout.compose.** { *; }
-keep class androidx.compose.ui.graphics.** { *; }
-keep class androidx.compose.ui.text.** { *; }

# Compose 注解保护
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
    @androidx.compose.runtime.Stable <methods>;
    @androidx.compose.runtime.Immutable <methods>;
    @androidx.compose.runtime.ReadOnlyComposable <methods>;
}

# Media3
-keep class androidx.media3.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class com.theveloper.pixelplay.data.service.workers.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Glance Widget
-keep class androidx.glance.** { *; }
-keep class com.theveloper.pixelplay.presentation.widgets.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# 数据模型
-keep class com.theveloper.pixelplay.data.model.** { *; }

# 搜索 API
-keep class com.theveloper.pixelplay.data.lx.** { *; }
-keep class com.theveloper.pixelplay.data.qq.** { *; }
-keep class com.theveloper.pixelplay.data.bilibili.** { *; }

# 网络
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }

# 安全加密
-keep class androidx.security.crypto.** { *; }

# 协程
-keep class kotlinx.coroutines.** { *; }

# =============================================================================
# 七、日志优化
# =============================================================================

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}

-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# =============================================================================
# 七、抑制警告
# =============================================================================

-dontwarn java.lang.management.**
-dontwarn reactor.blockhound.**
-dontwarn kotlinx.coroutines.**

-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.sound.sampled.**
-dontwarn javax.swing.**

-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**