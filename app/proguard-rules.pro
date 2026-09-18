# =============================================================================
# PixelPlayer ProGuard Rules
# Version: 2.2
# Last Updated: 2026-08-07
#
# 体积优化：移除包级全保留（-keep class X.** { *; }）规则。
# AndroidX / 主流三方库均自带 consumer rules，R8 会自动保留反射所需部分，
# 全 keep 只会关闭死代码裁剪导致 DEX 膨胀（此前 DEX 高达 32MB）。
# 仅保留确有反射 / JNI / ServiceLoader 需求的精确规则。
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

# AutoEQ：Gson 反射序列化/反序列化（autoeq_profiles.json 数据库 + 用户设备持久化）。
# R8 裁剪/混淆字段会导致 release 构建下数据库丢失、用户设备列表为空（debug 正常）。
-keep class com.theveloper.pixelplay.data.autoeq.** { *; }
-dontwarn com.theveloper.pixelplay.data.autoeq.**

# =============================================================================
# 三、第三方库规则（仅保留确有反射 / JNI / ServiceLoader 需求的部分）
# =============================================================================

# DI：保留 Hilt 生成的组件、工厂与注解成员
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keepclassmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <fields>;
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
}
-keep class dagger.hilt.android.AndroidEntryPoint { *; }
-keep class dagger.hilt.android.lifecycle.ViewModelInject { *; }

# TDLib：Java 绑定由 JNI 从 native 反射实例化，必须整包保留
-keep class org.drinkless.tdlib.** { *; }

# Ktor & Netty：引擎/通道工厂经反射创建
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

# TagLib / JAudioTagger：native 元数据解析
-keep class com.kyant.taglib.** { *; }
-dontwarn com.kyant.taglib.**
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Nothing Glyph Matrix SDK v2.0（com.nothing.ketchum.*）：GlyphMatrixController 通过反射加载，
# 若不 keep，R8 会把仅反射引用的 SDK 类从 DEX 中裁剪 → 运行时 ClassNotFoundException → 误判"不支持"
-keep class com.nothing.ketchum.** { *; }
-dontwarn com.nothing.ketchum.**

# ExoPlayer FFmpeg/MIDI：native 解码器 JNI 反射
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.ffmpeg.** { *; }
-keep class androidx.media3.decoder.midi.** { *; }
-keep class com.jsyn.** { *; }
-keep class com.softsynth.** { *; }
-dontwarn com.jsyn.**
-dontwarn com.softsynth.**

# Kuromoji / Pinyin4J：日语歌词分词 / 拼音（词库数据文件不受 R8 裁剪）
-keep class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**
-keep class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**

# javax.sound.sampled：jaudiotagger 音频元数据解析依赖
-keep class javax.sound.sampled.** { *; }

# JSON.org
-keep class org.json.** { *; }
-dontwarn org.json.**

# SLF4J：Ktor / Netty 日志门面
-keep class org.slf4j.** { *; }

# QuickJS JS引擎：JNI 反射
-keep class com.whitestein.jq.** { *; }
-dontwarn com.whitestein.jq.**
-keep class org.quickjs.** { *; }
-dontwarn org.quickjs.**

# Room：保留 DAO 注解方法（Room 库自带 consumer rules，无需整包 keep）
-keepclassmembers class com.theveloper.pixelplay.data.database.** {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# WorkManager：项目内 Worker 子类
-keep class com.theveloper.pixelplay.data.service.workers.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Glance Widget：ActionCallback / GlanceAppWidget 子类
-keep class com.theveloper.pixelplay.presentation.widgets.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# =============================================================================
# 四、应用核心模块（项目自身类，Gson/序列化/反射敏感，保守保留）
# =============================================================================

-keep class com.theveloper.pixelplay.data.database.** { *; }
-keep class com.theveloper.pixelplay.data.database.entities.** { *; }
-keep class com.theveloper.pixelplay.data.database.daos.** { *; }
-keep class com.theveloper.pixelplay.data.database.migrations.** { *; }
-keep class com.theveloper.pixelplay.data.backup.** { *; }
-keep class com.theveloper.pixelplay.data.ai.** { *; }
-keep class com.theveloper.pixelplay.data.model.** { *; }
-keep class com.theveloper.pixelplay.data.repository.LyricsRepositoryImpl$LyricsData { *; }
-keep class com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry { *; }
-keep class com.theveloper.pixelplay.data.telegram.TelegramStreamProxy { *; }

# Cast
-keep class com.theveloper.pixelplay.data.service.cast.CastOptionsProvider { *; }
-keep class * implements com.google.android.gms.cast.framework.OptionsProvider

# 搜索 API（JS 引擎反射调用）
-keep class com.theveloper.pixelplay.data.lx.** { *; }
-keep class com.theveloper.pixelplay.data.qq.** { *; }
-keep class com.theveloper.pixelplay.data.bilibili.** { *; }

# =============================================================================
# 五、Android组件（manifest 引用）
# =============================================================================

-keep class com.theveloper.pixelplay.PixelPlayApplication { *; }
-keep class com.theveloper.pixelplay.MainActivity { *; }
-keep class com.theveloper.pixelplay.SplashActivity { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

# =============================================================================
# 六、日志优化
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
