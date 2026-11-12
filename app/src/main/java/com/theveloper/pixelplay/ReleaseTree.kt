package com.theveloper.pixelplay

import android.util.Log
import timber.log.Timber

/**
 * A release-optimized Timber Tree that:
 * - Only logs WARN, ERROR, and WTF (suppresses VERBOSE, DEBUG, and INFO)
 * - Strips method/line information for performance
 * - Could be extended to report errors to crash analytics (e.g., Firebase Crashlytics)
 */
class ReleaseTree : Timber.Tree() {
    
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // 插件子系统（Xs* / LxSource*）在 release 也保留 DEBUG/INFO，便于真机排查
        if (tag != null && (tag.startsWith("Xs") || tag.startsWith("LxSource"))) {
            return priority >= Log.DEBUG
        }
        // 其它 tag 只保留 WARN 及以上
        return priority >= Log.WARN
    }
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (tag != null && (tag.startsWith("Xs") || tag.startsWith("LxSource"))) {
            when (priority) {
                Log.DEBUG -> Log.d(tag, message, t)
                Log.INFO -> Log.i(tag, message, t)
                Log.WARN -> Log.w(tag, message, t)
                Log.ERROR -> Log.e(tag, message, t)
                Log.ASSERT -> Log.wtf(tag, message, t)
            }
            return
        }
        // Skip if not loggable (redundant but explicit)
        if (priority < Log.WARN) return
        
        // Use Android's Log directly (stripped in release by R8 if configured)
        when (priority) {
            Log.WARN -> Log.w(tag, message, t)
            Log.ERROR -> Log.e(tag, message, t)
            Log.ASSERT -> Log.wtf(tag, message, t)
        }
        
        // TODO: Optionally report errors to crash analytics
        // if (priority >= Log.ERROR && t != null) {
        //     FirebaseCrashlytics.getInstance().recordException(t)
        // }
    }
}
