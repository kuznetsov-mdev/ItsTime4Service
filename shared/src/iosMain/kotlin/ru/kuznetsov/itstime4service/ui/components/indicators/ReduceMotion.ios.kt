package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

internal actual object ReduceMotion {
    @Composable
    actual fun isEnabled(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
}
