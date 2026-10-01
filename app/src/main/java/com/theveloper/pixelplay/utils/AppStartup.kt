package com.theveloper.pixelplay.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 冷启动优化工具：把「与首屏无关的初始化」挪到首帧绘制完成之后执行。
 *
 * 为什么需要它：冷启动时主线程在首帧之前的每一毫秒都会直接变成用户可见的等待。
 * 像统计 SDK 初始化、WorkManager 周期任务调度、JS 引擎预热这类工作，
 * 放到首帧之后执行既不影响功能，又能把时间还给首帧。
 *
 * 实现说明：
 * - Application.onCreate 阶段还没有任何 View/Window 可挂 post，因此用 Choreographer
 *   等两帧（第一帧完成布局与绘制）再执行；
 * - 兜底：万一进程没有可见 UI、一直收不到 vsync（例如只有后台服务的无界面启动），
 *   超过 [FALLBACK_DELAY_MS] 后也会在主线程上执行一次，保证初始化不会永远不执行；
 * - 调用线程不是主线程时会自动切回主线程再排队。
 */
object AppStartup {

    private const val FALLBACK_DELAY_MS = 1_500L

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 在首帧绘制完成后（主线程）执行 [block]，且保证只会执行一次。
     * 同一进程内多次调用互不影响，各自独立排队。
     */
    fun runAfterFirstFrame(block: () -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runAfterFirstFrame(block) }
            return
        }

        val dispatched = AtomicBoolean(false)
        val runOnce: () -> Unit = {
            if (dispatched.compareAndSet(false, true)) {
                runCatching(block)
            }
        }

        // 兜底：无界面进程收不到 vsync 时，也要在有限时间内完成初始化
        mainHandler.postDelayed(Runnable { runOnce() }, FALLBACK_DELAY_MS)
        awaitFrame(remainingFrames = 2, onFrame = runOnce)
    }

    private fun awaitFrame(remainingFrames: Int, onFrame: () -> Unit) {
        Choreographer.getInstance().postFrameCallback {
            if (remainingFrames > 1) {
                awaitFrame(remainingFrames - 1, onFrame)
            } else {
                onFrame()
            }
        }
    }
}

/**
 * 启动计时：记录进程启动起点，供启动动画按**设备实际加载耗时**自适应。
 *
 * 启动动画不应该在加载耗时之上再叠加一段固定等待：设备越快动画越完整，
 * 设备越慢动画越短，整体「启动 → 进入主界面」的观感时长基本恒定。
 */
object StartupTiming {

    /** 动画最短时长：再慢的设备也保留一点过渡，避免硬切 */
    private const val MIN_DURATION_MS = 160L

    private var startMs = 0L

    /** 在 Application.onCreate 最前面调用（越早越准确） */
    fun markProcessStart() {
        if (startMs == 0L) startMs = SystemClock.uptimeMillis()
    }

    /** 从进程启动到现在的耗时（毫秒） */
    fun elapsedMs(): Long =
        if (startMs == 0L) 0L else (SystemClock.uptimeMillis() - startMs).coerceAtLeast(0L)

    /**
     * 自适应动画时长：从 [budgetMs] 预算里扣掉已经花掉的加载时间。
     * 例：预算 600ms，设备已加载 150ms → 动画 450ms；已加载 800ms → 动画取下限 160ms。
     */
    fun adaptiveDurationMs(budgetMs: Long): Int =
        (budgetMs - elapsedMs()).coerceIn(MIN_DURATION_MS, budgetMs).toInt()
}
