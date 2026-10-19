package ru.kuznetsov.itstime4service

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.features.navigation.ItsTime4ServiceNavGraph
import ru.kuznetsov.itstime4service.ui.notification.rememberNotificationPermissionRequest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import ru.kuznetsov.itstime4service.ui.theme.SystemAppearance

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
    val launch by viewModel.launch.collectAsStateWithLifecycle()
    SystemAppearance(themeMode, darkTheme)
    ItsTime4ServiceTheme(darkTheme = darkTheme) {
        when (val current = launch) {
            Launch.Pending -> Box(Modifier.fillMaxSize().background(ItsTime4ServiceTheme.colors.bg))
            is Launch.Ready -> ItsTime4ServiceNavGraph(launchCarId = current.carId)
        }
    }
}
