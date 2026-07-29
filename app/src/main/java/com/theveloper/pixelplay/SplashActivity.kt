package com.theveloper.pixelplay

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val splashDuration: Long = 1200

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

            val backgroundColor = resolveThemeColor(android.R.attr.colorBackground, fallbackBackground)
            val foregroundColor = resolveThemeColor(android.R.attr.colorForeground, fallbackForeground)

            val root = FrameLayout(this).apply {
                setBackgroundColor(backgroundColor)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }

            val title = TextView(this).apply {
                text = "PixelPlayer"
                setTextColor(foregroundColor)
                textSize = 32f
                typeface = Typeface.SANS_SERIF
                alpha = 0f
            }

            content.addView(title)
            root.addView(content)
            setContentView(root)

            // 文字淡入动画
            val titleFade = AlphaAnimation(0f, 1f).apply {
                duration = 600
                fillAfter = true
            }
            title.startAnimation(titleFade)

            handler.postDelayed({
                navigateToMain()
                // 使用淡入淡出过渡，避免跳变
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
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
