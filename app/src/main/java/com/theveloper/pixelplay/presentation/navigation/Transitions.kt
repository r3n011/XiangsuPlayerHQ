package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

// MD3 Expressive – Emphasized easing (matches Material Motion spec)
// cubic-bezier(0.2, 0, 0, 1.0) — fast start, smooth settle
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Decelerate for elements entering the screen — mirror of EmphasizedAccelerateEasing
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)

// Accelerate for elements leaving the screen
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Base duration designed for 1x animation scale
const val TRANSITION_DURATION = 450

// ====== Push: Enter from Right (used for forward navigation) ======
// 简化：只使用 slide + fade，移除 scale，确保所有 tab 的动画感知一致
fun enterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

// ====== Push: Exit to Left ======
fun exitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

// ====== Pop: Enter from Left ======
fun popEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedDecelerateEasing)
)

// ====== Pop: Exit to Right ======
fun popExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.5f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)
