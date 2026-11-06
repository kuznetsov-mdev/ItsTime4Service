package ru.kuznetsov.itstime4service.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle

@Composable
internal actual fun rememberAppVersion(): AppVersion = remember {
    AppVersion(
        name = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "—",
        buildNumber = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "—",
    )
}
