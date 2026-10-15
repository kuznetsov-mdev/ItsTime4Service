package ru.kuznetsov.itstime4service

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.features.navigation.ItsTime4ServiceNavGraph
import ru.kuznetsov.itstime4service.ui.notification.rememberNotificationPermissionRequest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

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
    ItsTime4ServiceTheme(darkTheme = darkTheme) {
        ItsTime4ServiceNavGraph()
    }
}
