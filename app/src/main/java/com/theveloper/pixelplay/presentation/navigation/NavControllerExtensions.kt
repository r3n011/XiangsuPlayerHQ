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

fun NavController.navigateSafely(route: String): Boolean {
    if (!isReadyForNavigation()) return false
    return runNavigateCatching(route) {
        navigate(route) {
            launchSingleTop = true
        }
    }
}

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean {
    if (!isReadyForNavigation()) return false
    return runNavigateCatching(route) {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
    }
}

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
