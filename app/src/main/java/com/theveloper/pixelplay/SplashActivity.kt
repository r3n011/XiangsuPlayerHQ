package com.theveloper.pixelplay

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val splashDuration: Long = 1400

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.i("PixelPlay", "========================================")
        android.util.Log.i("PixelPlay", "SplashActivity.onCreate START")

        try {
            super.onCreate(savedInstanceState)
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "SplashActivity.super.onCreate() FAILED: ${t.message}", t)
            safeFinish()
            return
        }

        try {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val fallbackBackground = if (isNight) Color.BLACK else Color.WHITE
            val fallbackForeground = if (isNight) Color.WHITE else Color.BLACK
            val fallbackPrimary = Color.parseColor("#6750A4")

            val backgroundColor = resolveThemeColor(android.R.attr.colorBackground, fallbackBackground)
            val foregroundColor = resolveThemeColor(android.R.attr.colorForeground, fallbackForeground)
            val primaryColor = resolveThemeColor(android.R.attr.colorPrimary, fallbackPrimary)

            val root = FrameLayout(this).apply {
                setBackgroundColor(backgroundColor)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // 垂直居中的内容容器
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }

            // 标题 —— PixelPlay
            val title = TextView(this).apply {
                text = "PixelPlayer"
                setTextColor(foregroundColor)
                textSize = 30f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setPadding(0, 0, 0, 40)
                alpha = 0f
            }

            // 加载线容器（固定宽度，让线条在此范围内从中心扩展）
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    400,
                    6
                )
            }

            // 加载线本身
            val loadingBar = View(this).apply {
                setBackgroundColor(primaryColor)
                layoutParams = LinearLayout.LayoutParams(0, 6)
            }

            barContainer.addView(loadingBar)
            content.addView(title)
            content.addView(barContainer)
            root.addView(content)
            setContentView(root)

            // === 动画 1: 标题淡入 ===
            val titleFade = AlphaAnimation(0f, 1f).apply {
                duration = 500
                fillAfter = true
            }
            title.startAnimation(titleFade)

            // === 动画 2: 加载线从中心向两端扩展（从宽度 0 扩展到容器宽度） ===
            handler.postDelayed({
                val barWidth = 400
                val expandAnim = ScaleAnimation(
                    0f, 1f, 1f, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 700
                    interpolator = AccelerateDecelerateInterpolator()
                    fillAfter = true
                }
                loadingBar.layoutParams = loadingBar.layoutParams.apply {
                    width = barWidth
                }
                loadingBar.startAnimation(expandAnim)
            }, 200)

            // 延迟后跳转
            handler.postDelayed({
                navigateToMain()
                finish()
                overridePendingTransition(0, 0)
            }, splashDuration)

        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "SplashActivity setup FAILED: ${t.message}", t)
            safeFinish()
        }
    }

    private fun safeFinish() {
        try {
            navigateToMain()
            finish()
        } catch (_: Throwable) {}
    }

    private fun navigateToMain() {
        try {
            val mainIntent = Intent(this@SplashActivity, MainActivity::class.java)

            val source = intent
            if (source != null) {
                val sourceAction = source.action
                if (sourceAction != null && sourceAction != Intent.ACTION_MAIN) {
                    mainIntent.action = sourceAction
                }

                val sourceData = source.data
                if (sourceData != null) {
                    mainIntent.data = sourceData
                    mainIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    val sourceClip = source.clipData
                    if (sourceClip != null) {
                        mainIntent.clipData = sourceClip
                        mainIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }

                val sourceExtras = source.extras
                if (sourceExtras != null) {
                    mainIntent.putExtras(sourceExtras)
                }
            }

            startActivity(mainIntent)
            overridePendingTransition(0, 0)
        } catch (t: Throwable) {
            android.util.Log.e("PixelPlay", "navigateToMain ERROR: ${t.message}", t)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun resolveThemeColor(attrResId: Int, fallback: Int): Int {
        val typedValue = TypedValue()
        return if (theme.resolveAttribute(attrResId, typedValue, true)) {
            typedValue.data
        } else {
            fallback
        }
    }
}
