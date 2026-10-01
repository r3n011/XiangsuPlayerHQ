package com.theveloper.pixelplay.presentation.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * 悬浮歌词窗口（系统级 overlay）的 WindowManager 封装。
 *
 * 只负责「窗口」这一件事：加/删、平移、贴边收起与恢复、屏幕尺寸变化后重新落位。
 * 内容与手势交给 [FloatingLyricsView]，由它回调驱动这里。
 *
 * 「贴边」= 把歌词往屏幕边缘外推并松手 → 歌词缩进屏幕外，只在边缘留一个小箭头；
 * 点箭头（[exitEdgeHidden]）恢复成完整歌词。
 */
internal class FloatingLyricsWindow(
    private val context: Context,
    private val onPositionSettled: (x: Int, y: Int) -> Unit,
) {

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var rootView: FloatingLyricsView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null

    /** 收起前的可见坐标：箭头恢复时回到这里（落盘也用这个值）。 */
    private var visibleX = 0

    /**
     * 水平锚点：内容变宽/变窄时让窗口的**中心**保持不动。
     *
     * 窗口是 `WRAP_CONTENT`，宽度完全由当前这句歌词的长度决定，而 [WindowManager.LayoutParams.x]
     * 是**左边缘**坐标。以左边缘为锚的话，歌词一变长整条胶囊只往右撑、变短又缩回左边，
     * 视觉中心跟着来回跑 —— 看起来就像"自己飘走了"。
     * 这里在尺寸变化时反向补偿 x，让左右对称伸缩、中心固定。
     *
     * 贴边收起/恢复是刻意的宽度巨变，位置由 [tuckedX] / [visibleX] 决定，跳过补偿。
     */
    private val centerAnchorListener =
        View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val view = rootView ?: return@OnLayoutChangeListener
            if (view.isEdgeHidden()) return@OnLayoutChangeListener
            val newWidth = right - left
            val oldWidth = oldRight - oldLeft
            if (oldWidth <= 0 || newWidth <= 0 || newWidth == oldWidth) {
                return@OnLayoutChangeListener
            }
            val params = layoutParams ?: return@OnLayoutChangeListener
            params.x += (oldWidth - newWidth) / 2
            applyLayout()
            visibleX = params.x
        }

    val isShowing: Boolean get() = rootView != null
    val isEdgeHidden: Boolean get() = rootView?.isEdgeHidden() == true

    fun show(view: FloatingLyricsView, x: Int, y: Int) {
        if (rootView != null || windowManager == null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            // FLAG_NOT_FOCUSABLE：不抢焦点（否则会顶掉输入法/返回键）
            // 刻意**不加** FLAG_NOT_TOUCHABLE：拖动与点击都需要接收触摸
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        runCatching { windowManager?.addView(view, params) }
            .onSuccess {
                rootView = view
                layoutParams = params
                visibleX = x
                view.addOnLayoutChangeListener(centerAnchorListener)
            }
    }

    fun remove() {
        animator?.cancel()
        animator = null
        val view = rootView ?: return
        view.removeOnLayoutChangeListener(centerAnchorListener)
        rootView = null
        layoutParams = null
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    /**
     * 拖动中：按位移增量平移窗口。
     * 刻意允许往屏幕外推出去一点（[OVERSHOOT_FRACTION]），
     * 否则永远推不出边界，就无法触发「贴边收起」。
     */
    fun moveBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        val screen = screenSize()
        val overshoot = (view.width * OVERSHOOT_FRACTION).roundToInt()
        params.x = (params.x + dx.roundToInt())
            .coerceIn(-overshoot, (screen.width() - view.width) + overshoot)
        params.y = (params.y + dy.roundToInt())
            .coerceIn(0, (screen.height() - view.height).coerceAtLeast(0))
        applyLayout()
    }

    /**
     * 松手落位：
     * - 被推到边缘外侧（超过 [HIDE_TRIGGER_FRACTION] 的自身宽度）→ 收进边缘只留箭头；
     * - 否则夹回屏幕内并落盘。
     */
    fun settle(edgeHide: Boolean) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        val screen = screenSize()
        val width = view.width.takeIf { it > 0 } ?: view.edgeTabWidthPx()
        val threshold = (width * HIDE_TRIGGER_FRACTION).roundToInt()

        if (edgeHide && params.x <= -threshold) {
            enterEdgeHidden(params, view, screen, onLeft = true)
            return
        }
        if (edgeHide && params.x >= screen.width() - width + threshold) {
            enterEdgeHidden(params, view, screen, onLeft = false)
            return
        }

        val targetX = params.x.coerceIn(0, (screen.width() - width).coerceAtLeast(0))
        if (targetX != params.x) {
            animateTo(params, targetX) { onPositionSettled(params.x, params.y) }
        } else {
            visibleX = params.x
            onPositionSettled(params.x, params.y)
        }
    }

    /** 从贴边收起状态恢复成完整歌词，并滑回收起前的可见位置。 */
    fun exitEdgeHidden() {
        val params = layoutParams ?: return
        val view = rootView ?: return
        if (!view.isEdgeHidden()) return
        view.setEdgeHidden(false, false)
        val screen = screenSize()
        val targetX = visibleX.coerceIn(0, (screen.width() - view.width).coerceAtLeast(0))
        animateTo(params, targetX) { onPositionSettled(params.x, params.y) }
    }

    /** 旋转/分屏后按新屏幕尺寸重新落位（收起状态则重新贴到对应边缘）。 */
    fun clampToScreen() {
        val params = layoutParams ?: return
        val view = rootView ?: return
        val screen = screenSize()
        if (view.isEdgeHidden()) {
            val onLeft = params.x + view.width / 2 < screen.width() / 2
            params.y = params.y.coerceIn(0, (screen.height() - view.height).coerceAtLeast(0))
            params.x = if (onLeft) tuckedX(view, onLeft = true) else tuckedX(view, onLeft = false, screenWidth = screen.width())
            applyLayout()
            return
        }
        params.x = params.x.coerceIn(0, (screen.width() - view.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screen.height() - view.height).coerceAtLeast(0))
        applyLayout()
        visibleX = params.x
        onPositionSettled(params.x, params.y)
    }

    /**
     * 内容变高（展开控制条）或变矮后，把窗口夹回屏幕内。
     *
     * 与 [clampToScreen] 的区别：**不落盘**。展开一下就把用户摆好的位置覆盖掉、
     * 收起后窗口又停在别处，体验会很怪；这里只做一次性的临时校正。
     */
    fun ensureVisible() {
        val params = layoutParams ?: return
        val view = rootView ?: return
        if (view.isEdgeHidden()) return
        val screen = screenSize()
        val newX = params.x.coerceIn(0, (screen.width() - view.width).coerceAtLeast(0))
        val newY = params.y.coerceIn(0, (screen.height() - view.height).coerceAtLeast(0))
        if (newX == params.x && newY == params.y) return
        params.x = newX
        params.y = newY
        applyLayout()
        visibleX = newX
    }

    /* ---------------------------------------------------------------------- */
    /*                                  内部实现                                */
    /* ---------------------------------------------------------------------- */

    private fun enterEdgeHidden(
        params: WindowManager.LayoutParams,
        view: FloatingLyricsView,
        screen: Rect,
        onLeft: Boolean,
    ) {
        // 先记住可见位置（用于恢复与落盘），再切换形态
        visibleX = params.x.coerceIn(0, (screen.width() - view.width).coerceAtLeast(0))
        view.setEdgeHidden(true, onLeft)
        val targetX = tuckedX(view, onLeft, screen.width())
        animateTo(params, targetX) { onPositionSettled(visibleX, params.y) }
    }

    /**
     * 收起后箭头的位置：**完整**贴在屏幕内侧，并离边缘留出 [EDGE_TAB_MARGIN_DP]。
     *
     * 之前是让箭头一半缩到屏幕外（只露 55%），可见部分只能从 x = 0 起算，
     * 视觉上紧贴着边缘、和屏幕圆角 / 侧边手势区粘成一片，看起来像被裁掉了一块。
     */
    private fun tuckedX(
        view: FloatingLyricsView,
        onLeft: Boolean,
        screenWidth: Int = screenSize().width(),
    ): Int {
        val tab = view.edgeTabWidthPx()
        val margin = dp(EDGE_TAB_MARGIN_DP)
        return if (onLeft) margin else screenWidth - tab - margin
    }

    private fun dp(value: Float): Int =
        (context.resources.displayMetrics.density * value).roundToInt()

    private fun animateTo(
        params: WindowManager.LayoutParams,
        targetX: Int,
        onEnd: () -> Unit,
    ) {
        animator?.cancel()
        if (params.x == targetX) {
            onEnd()
            return
        }
        animator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_DURATION_MS
            addUpdateListener {
                params.x = it.animatedValue as Int
                applyLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    private fun applyLayout() {
        val view = rootView ?: return
        val params = layoutParams ?: return
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun screenSize(): Rect {
        val rect = Rect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.let { rect.set(it) }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getRectSize(rect)
        }
        if (rect.width() <= 0 || rect.height() <= 0) {
            rect.set(0, 0, DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT)
        }
        return rect
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            // minSdk 23：Android 8.0 以下只能用 TYPE_PHONE
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private companion object {
        const val SNAP_DURATION_MS = 180L

        /** 拖动时允许推到屏幕外的比例（自身宽度的百分比）。 */
        const val OVERSHOOT_FRACTION = 0.45f

        /** 推出边界超过自身宽度的这个比例，就判定为「贴边收起」。 */
        const val HIDE_TRIGGER_FRACTION = 0.12f

        /** 收起后箭头与屏幕左/右边缘的间距（dp）。 */
        const val EDGE_TAB_MARGIN_DP = 8f

        const val DEFAULT_SCREEN_WIDTH = 1080
        const val DEFAULT_SCREEN_HEIGHT = 1920
    }
}
