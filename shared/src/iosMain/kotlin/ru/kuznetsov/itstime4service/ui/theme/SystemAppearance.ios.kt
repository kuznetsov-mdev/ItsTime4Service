package ru.kuznetsov.itstime4service.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import ru.kuznetsov.itstime4service.domain.model.ThemeMode

@Composable
actual fun SystemAppearance(themeMode: ThemeMode, darkTheme: Boolean) {
    SideEffect {
        val style = when (themeMode) {
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        }
        UIApplication.sharedApplication.connectedScenes.forEach { scene ->
            (scene as? UIWindowScene)?.windows?.forEach { (it as? UIWindow)?.overrideUserInterfaceStyle = style }
        }
    }
}
