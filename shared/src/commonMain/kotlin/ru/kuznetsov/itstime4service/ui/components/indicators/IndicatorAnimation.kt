package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * How the status indicators move: a share fills from zero when the indicator appears and slides from the old value
 * to the new one when it changes; a color changes smoothly. In previews and with the system's reduce-motion setting
 * on, every value is shown at once.
 */
internal object IndicatorAnimation {
    const val DURATION_MS = 400

    @Composable
    fun fraction(target: Float): Float {
        if (isStill()) return target
        val animatable = remember { Animatable(0f) }
        LaunchedEffect(target) { animatable.animateTo(target, tween(DURATION_MS, easing = FastOutSlowInEasing)) }
        return animatable.value
    }

    @Composable
    fun color(target: Color): Color {
        if (isStill()) return target
        val color by animateColorAsState(target, tween(DURATION_MS, easing = FastOutSlowInEasing))
        return color
    }

    @Composable
    private fun isStill(): Boolean = LocalInspectionMode.current || ReduceMotion.isEnabled()
}
