package ru.kuznetsov.itstime4service.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat

@Composable
internal actual fun rememberAppVersion(): AppVersion {
    val context = LocalContext.current
    return remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        AppVersion(
            name = info.versionName ?: "—",
            buildNumber = PackageInfoCompat.getLongVersionCode(info).toString(),
        )
    }
}
