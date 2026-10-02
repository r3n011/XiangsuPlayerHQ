package com.theveloper.pixelplay.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import timber.log.Timber

private fun NavController.isReadyForNavigation(): Boolean {
    return runCatching {
        // Only block navigation if the current entry is DESTROYED (the graph is shutting down).
        // STARTED/RESUMED/CREATED are all fine — NavController will safely handle navigation even
        // if the entry is in a transient state (e.g. during transition, after mini-player swipe
        // dismiss, or while composables recompute). The previous STARTED check was too strict and
        // caused navigation to be silently skipped when lifecycle wasn't fully restored, leading
        // to the "bottom-nav only blurs but doesn't switch" bug after swipe-dismiss.
        currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.DESTROYED
    }.getOrDefault(false)
}

private const val MAX_NAVIGATION_RETRIES = 2
private const val NAVIGATION_RETRY_DELAY_MS = 48L

/**
 * Runs [block]; if the destination is not registered in the current navigation graph,
 * returns false instead of crashing with IllegalArgumentException.
 */
private inline fun NavController.runNavigateCatching(route: String, block: () -> Unit): Boolean {
    return try {
        block()
        true
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "navigateSafely: route '%s' not found in navigation graph", route)
        false
    }
}

/**
 * 用户点击触发的导航不能「点了没反应」：生命周期 / 回退栈正在切换时（刚 pop 完、
 * 从登录 Activity 返回、转场动画中）NavController 会短暂不可用，此前直接丢弃，
 * 表现就是「有时能打开有时打不开」。这里挂到下一帧重试（最多 [MAX_NAVIGATION_RETRIES] 次），
 * 重试次数由本次调用统一计数，不会无限循环。
 */
private fun NavController.navigateWithRetry(
    route: String,
    builder: (NavOptionsBuilder.() -> Unit)? = null,
    attempt: Int = 0,
): Boolean {
    if (!isReadyForNavigation()) {
        if (attempt >= MAX_NAVIGATION_RETRIES) return false
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { navigateWithRetry(route, builder, attempt + 1) },
            NAVIGATION_RETRY_DELAY_MS,
        )
        return true
    }
    return runNavigateCatching(route) {
        navigate(route) {
            launchSingleTop = true
            builder?.invoke(this)
        }
    }
}

fun NavController.navigateSafely(route: String): Boolean = navigateWithRetry(route)

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean = navigateWithRetry(route, builder)

fun NavController.navigateSafelyReplacing(
    route: String,
    patternToPop: String,
    builder: NavOptionsBuilder.() -> Unit = {}
): Boolean {
    if (!isReadyForNavigation()) return false
    return runNavigateCatching(route) {
        navigate(route) {
            launchSingleTop = false
            popUpTo(patternToPop) {
                inclusive = true
            }
            builder()
        }
    }
}

fun NavController.navigateToTopLevelSafely(route: String): Boolean {
    if (!isReadyForNavigation()) return false
    val startDestinationId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    return runNavigateCatching(route) {
        navigate(route) {
            popUpTo(startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}
