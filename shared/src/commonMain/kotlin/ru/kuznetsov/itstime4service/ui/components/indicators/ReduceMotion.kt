package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.runtime.Composable

/**
 * Whether the system asks apps to reduce motion: on Android the animator duration scale set to off, on iOS
 * the Reduce Motion accessibility setting. Read once per composition of the caller.
 */
internal expect object ReduceMotion {
    @Composable
    fun isEnabled(): Boolean
}
