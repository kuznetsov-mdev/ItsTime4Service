package ru.kuznetsov.itstime4service.ui.theme

import androidx.compose.runtime.Composable
import ru.kuznetsov.itstime4service.domain.model.ThemeMode

/**
 * Makes the system parts of the screen follow the theme picked in the app rather than the system one:
 * on Android the status and navigation bar icons, on iOS the status bar, the keyboard and the system controls.
 * [darkTheme] is the theme the app is drawn in; [themeMode] tells whether it was picked or follows the system.
 */
@Composable
expect fun SystemAppearance(themeMode: ThemeMode, darkTheme: Boolean)
