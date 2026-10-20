package ru.kuznetsov.itstime4service.ui.components.indicators

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal actual object ReduceMotion {
    @Composable
    actual fun isEnabled(): Boolean {
        val resolver = LocalContext.current.contentResolver
        return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    }
}
