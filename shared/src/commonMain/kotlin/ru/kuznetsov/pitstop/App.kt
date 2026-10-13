package ru.kuznetsov.pitstop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.kuznetsov.pitstop.domain.model.ThemeMode
import ru.kuznetsov.pitstop.features.navigation.PitStopNavGraph
import ru.kuznetsov.pitstop.ui.notification.rememberNotificationPermissionRequest
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val needsNotificationPermission by viewModel.needsNotificationPermission.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest(viewModel::onNotificationPermissionResult)
    LaunchedEffect(needsNotificationPermission) {
        if (needsNotificationPermission) requestNotificationPermission()
    }
    PitStopTheme(darkTheme = darkTheme) {
        PitStopNavGraph()
    }
}
