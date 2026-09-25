package com.theveloper.pixelplay.data.analytics

import android.content.Context
import com.theveloper.pixelplay.BuildConfig
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure

/**
 * 友盟移动统计 U-App 的统一接入入口。
 *
 * 涉及依赖（见 gradle/libs.versions.toml）：
 *  - com.umeng.umsdk:asms   —— 友盟公共基础组件，全部友盟业务 SDK 共用，全工程只声明一次
 *  - com.umeng.umsdk:common —— 统计业务 SDK，提供 UMConfigure 初始化与 MobclickAgent 埋点能力
 *
 * 初始化顺序（官方要求，不可颠倒）：
 *  1. [preInit]  —— 必须在 Application.onCreate 主线程最早执行；不采集设备信息、不上报数据
 *  2. [init]     —— 真正初始化，开始按策略采集并上报
 *  3. [sendOnboardingTestEvent] —— 埋点上报自定义事件
 *
 * 合规提示：官方要求 init 需在用户同意隐私政策之后调用。当前工程没有隐私政策同意流程，
 * 因此暂时紧随 preInit 调用 init。若后续引入隐私门控，应把 [init] 移到用户同意之后。
 */
object UmengAnalytics {

    /** 友盟后台该应用对应的 AppKey */
    const val APP_KEY = "6ab67c9a6545637cd6f19cae"

    /** 渠道名。未做多渠道打包，统一使用商店渠道标识 */
    const val CHANNEL = "GooglePlay"

    /** 接入验证用的自定义事件 ID */
    const val ONBOARDING_TEST_EVENT = "umeng_onboarding_test"

    @Volatile
    private var preInitialized = false

    @Volatile
    private var initialized = false

    /**
     * 预初始化。必须在主线程调用，且应早于任何其它友盟 SDK 调用。
     * 该阶段不会采集设备信息，也不会上报数据，仅用于尽早加载 SDK。
     */
    fun preInit(context: Context) {
        if (preInitialized) return
        try {
            UMConfigure.preInit(context.applicationContext, APP_KEY, CHANNEL)
            preInitialized = true
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "Failed to preInit Umeng: ${t.message}")
        }
    }

    /**
     * 正式初始化统计 SDK，之后开始上报数据。
     * [UMConfigure.DEVICE_TYPE_PHONE] 表示手机/平板设备类型，pushSecret 未接入推送时传 null。
     */
    fun init(context: Context) {
        if (initialized) return
        try {
            val appContext = context.applicationContext
            // 集成期打开调试日志，便于在 Logcat 中确认埋点是否上报（release 构建会被 ProGuard 剥离）
            UMConfigure.setLogEnabled(BuildConfig.DEBUG)
            // 官方建议：应用可能存在多个进程时开启，避免子进程重复统计（本项目自身未声明多进程，此处为防御性配置）
            UMConfigure.setProcessEvent(true)
            UMConfigure.init(
                appContext,
                APP_KEY,
                CHANNEL,
                UMConfigure.DEVICE_TYPE_PHONE,
                null
            )
            initialized = true
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "Failed to init Umeng: ${t.message}")
        }
    }

    /** 上报自定义事件 [ONBOARDING_TEST_EVENT]，用于验证统计 SDK 的埋点通道是否打通 */
    fun sendOnboardingTestEvent(context: Context) {
        try {
            MobclickAgent.onEvent(context.applicationContext, ONBOARDING_TEST_EVENT)
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "Failed to send Umeng event: ${t.message}")
        }
    }
}
