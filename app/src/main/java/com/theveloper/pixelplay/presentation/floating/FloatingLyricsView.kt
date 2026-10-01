package com.theveloper.pixelplay.presentation.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlin.math.abs
import kotlin.math.roundToInt

/** 贴边收起时露出的箭头宽度（dp）：[FloatingLyricsWindow] 算缩进位置时也用它。 */
internal const val FLOATING_LYRICS_EDGE_TAB_WIDTH_DP = 30f

/**
 * 悬浮歌词的内容视图。
 *
 * 刻意**不用 Compose**：悬浮窗挂在 WindowManager 上、没有 Activity 宿主，`ComposeView`
 * 会通过 ViewTree 查找三个 Owner，缺一个就崩；而这些 Owner 的挂载 API 在当前 lifecycle
 * 版本对应用层不可见。浮层内容很简单，View 实现更稳、开销也更小。
 *
 * 视觉对齐软件本身：半透明圆角胶囊（24dp 圆角 + 1dp 描边，配色取 M3 的
 * surfaceContainerHigh / onSurface 深浅两套）、软件自带字体（gflex_variable）与
 * 软件内的 Material 图标，跟随 App 的深浅色模式。
 *
 * 两种形态：
 * - 正常：胶囊里的 1~3 行歌词 + 可展开的控制条；
 * - 贴边收起：只剩一个贴着屏幕边缘的小箭头，点它恢复。
 */
@SuppressLint("ViewConstructor")
internal class FloatingLyricsView(
    context: Context,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onRestoreFromEdge: () -> Unit,
) : FrameLayout(context) {

    private val appTypeface: Typeface? = runCatching {
        ResourcesCompat.getFont(context, R.font.gflex_variable)
    }.getOrNull()

    private val prevText = createLine(fontSizeSp = SECONDARY_TEXT_SP, alpha = 0.6f)
    private val currentText = createLine(fontSizeSp = CURRENT_TEXT_SP, alpha = 1f, bold = true)
    private val nextText = createLine(fontSizeSp = SECONDARY_TEXT_SP, alpha = 0.65f)
    private val emptyText = createLine(fontSizeSp = SECONDARY_TEXT_SP, alpha = 0.65f).apply {
        text = "暂无歌词"
    }

    // 展开时给的是「歌曲」控制（切上一首/下一首），不是歌词行跳转，描述必须跟行为一致
    private val previousButton =
        createButton(R.drawable.rounded_skip_previous_24, "上一首", onPrevious)
    private val playPauseButton =
        createButton(R.drawable.rounded_play_arrow_24, "播放/暂停", onPlayPause)
    private val nextButton =
        createButton(R.drawable.rounded_skip_next_24, "下一首", onNext)

    private val controlBar = StrictLinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(previousButton)
        addView(playPauseButton)
        addView(nextButton)
    }

    /** 展开时在控制条上方显示「歌曲名 · 歌手」，让用户知道自己在控制哪首歌。 */
    private val trackText = createLine(fontSizeSp = TRACK_TEXT_SP, alpha = 0.95f, bold = true)

    /**
     * 展开区（歌曲信息 + 控制条）整体作为一个容器。
     *
     * 单独包一层是为了能对「展开/收起」做高度 + 淡入动画 —— 直接切两个子 View 的
     * VISIBLE/GONE 是硬跳，歌词会瞬间位移。
     */
    private val controlsGroup = StrictLinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
        addView(trackText, linearWrapParams())
        addView(controlBar, linearWrapParams())
    }

    /** 正常形态：一层半透明圆角胶囊包住控制区与歌词。 */
    private val lyricsPill = StrictLinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(controlsGroup, linearWrapParams())
        addView(prevText, linearWrapParams())
        addView(currentText, linearWrapParams())
        addView(nextText, linearWrapParams())
        addView(emptyText, linearWrapParams())
    }

    private val edgeTabIcon = ImageView(context).apply {
        setImageResource(R.drawable.rounded_chevron_right_24)
        contentDescription = "展开悬浮歌词"
        val pad = dp(4f)
        setPadding(pad, pad, pad, pad)
    }

    /** 贴边形态：只露一个贴边小箭头。 */
    private val edgeTab = FrameLayout(context).apply {
        visibility = View.GONE
        addView(
            edgeTabIcon,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        setOnClickListener { onRestoreFromEdge() }
    }

    private var locked = false
    private var edgeHidden = false
    private var edgeHiddenOnLeft = true
    private var isDark = true
    private var expanded = false

    /** 整体大小倍率（设置里的「整体大小」百分比 / 100）。 */
    private var scale = 1f

    /** 背景不透明度（0 = 完全透明，只留文字）。 */
    private var backgroundAlpha = 0.85f

    /** 展开/收起、整体大小变化是否走过渡动画（设置里可关）。 */
    private var animationsEnabled = true

    /** 展开区的高度动画（同一个 animator 同时驱动 alpha），避免快速点按时叠加。 */
    private var controlsAnimator: ValueAnimator? = null

    init {
        // 胶囊与箭头之间留一点空隙不会被裁掉；窗口本身 WRAP_CONTENT，不受影响
        addView(lyricsPill, wrapParams())
        addView(
            edgeTab,
            LayoutParams(dp(FLOATING_LYRICS_EDGE_TAB_WIDTH_DP), dp(EDGE_TAB_HEIGHT_DP))
        )
        applyAppearance(scale = DEFAULT_SCALE, backgroundAlpha = DEFAULT_BACKGROUND_ALPHA)
        installTouchHandling()
    }

    /* ---------------------------------------------------------------------- */
    /*                                  对外接口                                */
    /* ---------------------------------------------------------------------- */

    /** 跟随 App 深浅色切换配色（胶囊底色 / 描边 / 文字与图标颜色）。 */
    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        val (pillColor, strokeColor, contentColor) = if (isDark) {
            Triple(DARK_PILL, DARK_STROKE, DARK_CONTENT)
        } else {
            Triple(LIGHT_PILL, LIGHT_STROKE, LIGHT_CONTENT)
        }
        // 背景不透明度可调（0 = 完全透明，只剩文字与描边）
        val pill = withAlpha(pillColor, backgroundAlpha)
        val stroke = withAlpha(strokeColor, alphaOf(strokeColor) * backgroundAlpha)

        lyricsPill.background = GradientDrawable().apply {
            cornerRadius = dp(PILL_CORNER_DP * scale).toFloat()
            setColor(pill)
            setStroke(dp(1f), stroke)
        }
        controlBar.background = GradientDrawable().apply {
            cornerRadius = dp(50f).toFloat()
            setColor(withAlpha(if (isDark) DARK_CONTROL_BAR else LIGHT_CONTROL_BAR, backgroundAlpha))
        }

        listOf(trackText, prevText, currentText, nextText, emptyText)
            .forEach { it.setTextColor(contentColor) }
        listOf(previousButton, playPauseButton, nextButton).forEach {
            it.setColorFilter(contentColor)
        }
        edgeTab.background = GradientDrawable().apply {
            // 圆角取「胶囊圆角」与「箭头宽度的一半」的较小值：宽度只有 26dp，
            // 直接用 24dp 会溢出，取 13dp 恰好是个两端全圆的小胶囊。
            cornerRadius = dp(minOf(PILL_CORNER_DP, FLOATING_LYRICS_EDGE_TAB_WIDTH_DP / 2f) * scale).toFloat()
            setColor(pill)
            setStroke(dp(1f), stroke)
        }
        edgeTabIcon.setColorFilter(contentColor)
    }

    /**
     * 整体大小与背景不透明度。
     *
     * 大小会把字号、内边距、控制条按钮、贴边箭头**一起**缩放，
     * 这样放大后各元素比例不变（只放字号会显得挤）。
     */
    fun applyAppearance(scale: Float, backgroundAlpha: Float) {
        val newScale = scale.coerceIn(0.5f, 2f)
        val previousScale = this.scale
        this.scale = newScale
        this.backgroundAlpha = backgroundAlpha.coerceIn(0f, 1f)
        applyTypography()
        applyTheme(isDark)

        // 整体大小变化：先按「旧/新」比例反向缩放一下，再弹回 1。
        // 窗口本身是 WRAP_CONTENT，尺寸只能瞬间变，这层反向缩放让观感上是"长出来/缩回去"
        // 而不是硬跳（设置里可关）。
        if (animationsEnabled && previousScale > 0f && previousScale != newScale) {
            val ratio = (previousScale / newScale).coerceIn(0.6f, 1.6f)
            lyricsPill.scaleX = ratio
            lyricsPill.scaleY = ratio
            lyricsPill.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(SCALE_ANIM_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** 展开/收起与大小变化是否走过渡动画。 */
    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
    }

    /** 按当前 [scale] 重新计算所有尺寸（字号 / 内边距 / 按钮 / 箭头）。 */
    private fun applyTypography() {
        val secondary = SECONDARY_TEXT_SP * scale
        trackText.setTextSize(TypedValue.COMPLEX_UNIT_SP, TRACK_TEXT_SP * scale)
        prevText.setTextSize(TypedValue.COMPLEX_UNIT_SP, secondary)
        nextText.setTextSize(TypedValue.COMPLEX_UNIT_SP, secondary)
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, secondary)
        currentText.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            (if (expanded) CURRENT_TEXT_EXPANDED_SP else CURRENT_TEXT_SP) * scale
        )

        val buttonSize = dp(32f * scale)
        val buttonPad = dp(6f * scale)
        listOf(previousButton, playPauseButton, nextButton).forEach {
            // ⚠️ 按钮的父容器是 LinearLayout(controlBar)，这里必须给 LinearLayout.LayoutParams：
            //    类内部不加前缀的 LayoutParams 解析为 FrameLayout.LayoutParams（本类继承 FrameLayout），
            //    直接 setLayoutParams 不会经过父容器转换，LinearLayout 测量时强转就会 ClassCastException。
            it.layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
            it.setPadding(buttonPad, buttonPad, buttonPad, buttonPad)
        }
        controlBar.setPadding(dp(4f * scale), dp(2f * scale), dp(4f * scale), dp(2f * scale))
        lyricsPill.setPadding(
            dp(16f * scale),
            dp(10f * scale),
            dp(16f * scale),
            dp(10f * scale)
        )

        val tabWidth = edgeTabWidthPx()
        val tabHeight = dp(EDGE_TAB_HEIGHT_DP * scale)
        // 必须保留 setEdgeHidden 设过的贴边方向（START/END + 垂直居中）：
        // 换掉 layoutParams 会把 gravity 一起丢掉，贴边时箭头就不垂直居中了
        val tabGravity = (edgeTab.layoutParams as? LayoutParams)?.gravity
            ?: (Gravity.START or Gravity.CENTER_VERTICAL)
        edgeTab.layoutParams = LayoutParams(tabWidth, tabHeight).also { it.gravity = tabGravity }
        val iconSize = dp(20f * scale)
        edgeTabIcon.layoutParams =
            LayoutParams(iconSize, iconSize, Gravity.CENTER).also {
                edgeTabIcon.setPadding(dp(2f * scale), dp(2f * scale), dp(2f * scale), dp(2f * scale))
            }
        edgeTab.requestLayout()
        refreshMaxWidth()
    }

    /** 切到「贴边收起」形态：隐藏歌词胶囊、只留箭头（宽度立刻变小，窗口随即缩进边缘）。 */
    fun setEdgeHidden(hidden: Boolean, onLeft: Boolean) {
        if (edgeHidden == hidden && edgeHiddenOnLeft == onLeft) return
        edgeHidden = hidden
        edgeHiddenOnLeft = onLeft
        lyricsPill.visibility = if (hidden) View.GONE else View.VISIBLE
        edgeTab.visibility = if (hidden) View.VISIBLE else View.GONE
        // 箭头方向永远指向屏幕内侧
        edgeTabIcon.scaleX = if (onLeft) 1f else -1f
        (edgeTab.layoutParams as? LayoutParams)?.gravity =
            if (onLeft) Gravity.START or Gravity.CENTER_VERTICAL
            else Gravity.END or Gravity.CENTER_VERTICAL
        edgeTab.requestLayout()
    }

    fun isEdgeHidden(): Boolean = edgeHidden

    /** 贴边箭头的宽度（px）：[FloatingLyricsWindow] 用它算缩进后的位置（跟随整体大小）。 */
    fun edgeTabWidthPx(): Int = dp(FLOATING_LYRICS_EDGE_TAB_WIDTH_DP * scale)

    /** 按最新状态刷新文字、行数模式、控制条与播放/暂停图标。 */
    fun applyState(state: FloatingLyricsUiState) {
        refreshMaxWidth()
        locked = state.locked

        // 只有展开态真的变了才启动动画，避免每次 uiState 刷新都重放一遍
        if (expanded != state.expanded) {
            expanded = state.expanded
            animateControls(expanded)
        }

        val showLyrics = state.hasLyrics
        val showPrev = showLyrics &&
            state.lineMode == UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_MULTI &&
            state.prevLine.isNotBlank()
        val showNext = showLyrics &&
            state.lineMode != UserPreferencesRepository.FLOATING_LYRICS_LINE_MODE_SINGLE &&
            state.nextLine.isNotBlank()

        // 歌曲信息有没有内容决定它自己显不显示；整个展开区的显隐交给 controlsGroup
        val trackLabel = buildString {
            if (state.songTitle.isNotBlank()) {
                append(state.songTitle)
                if (state.songArtist.isNotBlank()) append(" · ").append(state.songArtist)
            }
        }
        trackText.text = trackLabel
        trackText.visibility = if (trackLabel.isNotBlank()) View.VISIBLE else View.GONE

        currentText.text = if (showLyrics) state.currentLine else ""
        prevText.text = state.prevLine
        nextText.text = state.nextLine

        currentText.visibility =
            if (showLyrics && state.currentLine.isNotBlank()) View.VISIBLE else View.GONE
        prevText.visibility = if (showPrev) View.VISIBLE else View.GONE
        nextText.visibility = if (showNext) View.VISIBLE else View.GONE
        emptyText.visibility = if (showLyrics) View.GONE else View.VISIBLE

        currentText.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            (if (state.expanded) CURRENT_TEXT_EXPANDED_SP else CURRENT_TEXT_SP) * scale
        )

        playPauseButton.setImageResource(
            if (state.isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24
        )
        playPauseButton.setColorFilter(if (isDark) DARK_CONTENT else LIGHT_CONTENT)
    }

    /**
     * 长句约束：单行文字最大占屏宽 [MAX_WIDTH_FRACTION]（去掉胶囊左右内边距），
     * 超长自动换行，保证浮层本身不会比屏幕还宽（旋转后也会重新取值）。
     */
    fun refreshMaxWidth() {
        val screen = resources.displayMetrics.widthPixels
        // 胶囊左右各 16dp 内边距，随整体大小一起缩放，否则放大后文字会撑出胶囊
        val max = (screen * MAX_WIDTH_FRACTION).roundToInt() - dp(32f * scale)
        trackText.maxWidth = max
        prevText.maxWidth = max
        currentText.maxWidth = max
        nextText.maxWidth = max
        emptyText.maxWidth = max
    }

    /* ---------------------------------------------------------------------- */
    /*                                  内部实现                                */
    /* ---------------------------------------------------------------------- */

    /**
     * 展开 / 收起展开区。
     *
     * 关掉动画时直接切 VISIBLE/GONE（旧行为）；开着时插值高度 + 透明度，
     * 歌词是「被推上去 / 放下来」而不是瞬间跳位。
     */
    private fun animateControls(expand: Boolean) {
        controlsAnimator?.cancel()
        controlsAnimator = null

        if (!animationsEnabled) {
            controlsGroup.alpha = 1f
            setControlsHeight(LinearLayout.LayoutParams.WRAP_CONTENT, visible = expand)
            return
        }

        if (expand) {
            val target = measureControlsHeight()
            if (target <= 0) {
                controlsGroup.alpha = 1f
                setControlsHeight(LinearLayout.LayoutParams.WRAP_CONTENT, visible = true)
                return
            }
            // 从当前实际高度接着做，快速连点也不会跳
            val current = if (controlsGroup.visibility == View.VISIBLE) controlsGroup.height else 0
            controlsAnimator = ValueAnimator.ofInt(current, target).apply {
                duration = CONTROLS_ANIM_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val h = anim.animatedValue as Int
                    setControlsHeight(h, visible = true)
                    controlsGroup.alpha = h.toFloat() / target
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // 动画用的是固定高度，结束后放开约束让内容能自适应
                        controlsGroup.alpha = 1f
                        setControlsHeight(LinearLayout.LayoutParams.WRAP_CONTENT, visible = true)
                    }
                })
                start()
            }
        } else {
            val start = controlsGroup.height
            if (start <= 0) {
                controlsGroup.alpha = 1f
                setControlsHeight(LinearLayout.LayoutParams.WRAP_CONTENT, visible = false)
                return
            }
            controlsAnimator = ValueAnimator.ofInt(start, 0).apply {
                duration = CONTROLS_ANIM_MS
                interpolator = AccelerateInterpolator()
                addUpdateListener { anim ->
                    val h = anim.animatedValue as Int
                    setControlsHeight(h, visible = true)
                    controlsGroup.alpha = h.toFloat() / start
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        controlsGroup.alpha = 1f
                        setControlsHeight(LinearLayout.LayoutParams.WRAP_CONTENT, visible = false)
                    }
                })
                start()
            }
        }
    }

    /** 量出展开区的自然高度：临时设为可见并放开高度约束，量完还原。 */
    private fun measureControlsHeight(): Int {
        val lp = controlsGroup.layoutParams as? LinearLayout.LayoutParams ?: return 0
        val savedHeight = lp.height
        val savedVisibility = controlsGroup.visibility

        lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
        controlsGroup.visibility = View.VISIBLE
        controlsGroup.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measured = controlsGroup.measuredHeight

        lp.height = savedHeight
        controlsGroup.visibility = savedVisibility
        return measured
    }

    private fun setControlsHeight(height: Int, visible: Boolean) {
        val lp = controlsGroup.layoutParams as? LinearLayout.LayoutParams
        if (lp != null && lp.height != height) {
            lp.height = height
            controlsGroup.layoutParams = lp
        }
        controlsGroup.visibility = if (visible) View.VISIBLE else View.GONE
        controlsGroup.requestLayout()
    }

    /**
     * 拖动 / 点击 / 长按判定：
     * - 位移超过 touchSlop → 拖动（按增量回调给 [FloatingLyricsWindow]）；
     * - 否则抬手 → 点击（展开/收起控制条）；
     * - 按住到长按超时 → 锁定/解锁。
     * 贴边箭头是子 View 且自身可点击，会先吃掉自己的点击，不会走到这里。
     */
    private fun installTouchHandling() {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var dragged = false
        var longPressed = false
        val longPressRunnable = Runnable {
            longPressed = true
            onLongPress()
        }

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = downRawX
                    lastRawY = downRawY
                    dragged = false
                    longPressed = false
                    postDelayed(longPressRunnable, longPressTimeout)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragged &&
                        abs(event.rawX - downRawX) + abs(event.rawY - downRawY) > touchSlop
                    ) {
                        dragged = true
                        removeCallbacks(longPressRunnable)
                    }
                    if (dragged && !locked) {
                        onDrag(event.rawX - lastRawX, event.rawY - lastRawY)
                    }
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPressRunnable)
                    if (dragged) {
                        onDragEnd()
                    } else if (!longPressed) {
                        onTap()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPressRunnable)
                    if (dragged) onDragEnd()
                    true
                }

                else -> false
            }
        }
    }

    private fun createLine(fontSizeSp: Float, alpha: Float, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.alpha = alpha
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            appTypeface?.let { setTypeface(it, if (bold) Typeface.BOLD else Typeface.NORMAL) }
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

    private fun createButton(
        iconRes: Int,
        description: String,
        onClick: () -> Unit,
    ): ImageView = ImageView(context).apply {
        setImageResource(iconRes)
        contentDescription = description
        val size = dp(32f)
        val pad = dp(6f)
        setPadding(pad, pad, pad, pad)
        // 父容器是 LinearLayout(controlBar)
        layoutParams = LinearLayout.LayoutParams(size, size)
        setOnClickListener { onClick() }
    }

    /** 本类（FrameLayout）子 View 用。 */
    private fun wrapParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    /**
     * LinearLayout 子 View 用。
     *
     * ⚠️ 不能复用 [wrapParams]：不加前缀的 `LayoutParams` 在本类内部解析为
     * `FrameLayout.LayoutParams`，塞进 LinearLayout 会在 measure 时强转崩溃。
     */
    private fun linearWrapParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun dp(value: Float): Int = (resources.displayMetrics.density * value).roundToInt()

    /** 取颜色的 alpha（0~1）。 */
    private fun alphaOf(color: Int): Float = ((color ushr 24) and 0xFF) / 255f

    /** 用给定 alpha 替换颜色的透明度，保留其 RGB。 */
    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)

    private companion object {
        /** 展开 / 收起展开区的动画时长。 */
        const val CONTROLS_ANIM_MS = 180L

        /** 整体大小变化时那一下缩放回弹的时长。 */
        const val SCALE_ANIM_MS = 160L

        const val TRACK_TEXT_SP = 13f
        const val CURRENT_TEXT_SP = 17f
        const val CURRENT_TEXT_EXPANDED_SP = 20f
        const val SECONDARY_TEXT_SP = 14f
        const val PILL_CORNER_DP = 24f
        const val EDGE_TAB_HEIGHT_DP = 56f
        const val MAX_WIDTH_FRACTION = 0.86f
        const val DEFAULT_SCALE = 1f
        const val DEFAULT_BACKGROUND_ALPHA = 0.85f

        // 与 M3 surfaceContainerHigh / onSurface / outlineVariant 对齐的深浅两套配色。
        // 用 val 而非 const：0xD9… 这类字面量需要 .toInt()，不是编译期常量表达式。
        val DARK_PILL = 0xD92B2930.toInt()
        val DARK_STROKE = 0x26FFFFFF
        val DARK_CONTENT = 0xFFE6E1E5.toInt()
        val DARK_CONTROL_BAR = 0x26FFFFFF
        val LIGHT_PILL = 0xE6ECE6F0.toInt()
        val LIGHT_STROKE = 0x1F000000
        val LIGHT_CONTENT = 0xFF1D1B20.toInt()
        val LIGHT_CONTROL_BAR = 0x14000000
    }
}

/**
 * 只接受 [LinearLayout.LayoutParams] 的 LinearLayout。
 *
 * 存在的理由：[FloatingLyricsView] 继承 `FrameLayout`，所以**在这个文件里**不加前缀的
 * `LayoutParams` 会被 Kotlin 解析成 `FrameLayout.LayoutParams`。而 `setLayoutParams()`
 * 不经过父容器的类型转换，只要有一处写漏前缀，`LinearLayout.measureHorizontal/Vertical`
 * 里那句 `(LayoutParams) child.getLayoutParams()` 就会直接 ClassCastException 崩掉
 * —— 崩溃栈指向 LinearLayout 内部，很难反推到是哪一行写漏了。
 *
 * 与其要求每个 setLayoutParams 的入口都人工小心，不如在测量前统一纠正一次：
 * 之后再写漏前缀，也只是被静默改成正确类型，不会再崩。
 */
private class StrictLinearLayout(context: Context) : LinearLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val params = child.layoutParams ?: continue
            if (params !is LinearLayout.LayoutParams) {
                // LinearLayout.LayoutParams 有接收 ViewGroup.LayoutParams 的构造函数，
                // margin 会一并保留
                child.layoutParams = LinearLayout.LayoutParams(params)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
