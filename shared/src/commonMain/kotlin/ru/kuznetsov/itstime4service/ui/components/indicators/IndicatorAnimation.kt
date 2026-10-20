package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * How the status indicators move: a share fills from zero when the indicator appears and slides from the old value
 * to the new one when it changes; a color changes smoothly. A share does not start filling while its screen is still
 * moving in — it waits, empty, until [LocalScreenSettled] says the screen transition is over (the navigation graph
 * provides it for every destination), so the two animations do not overlap. In previews and with the system's reduce-motion setting on, every value is shown at once.
 */
internal val LocalScreenSettled = compositionLocalOf { true }

internal object IndicatorAnimation {
    const val DURATION_MS = 400

    @Composable
    fun fraction(target: Float): Float {
        if (isStill()) return target
        val settled = LocalScreenSettled.current
        val animatable = remember { Animatable(0f) }
        LaunchedEffect(target, settled) {
            if (settled) animatable.animateTo(target, tween(DURATION_MS, easing = FastOutSlowInEasing))
        }
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
