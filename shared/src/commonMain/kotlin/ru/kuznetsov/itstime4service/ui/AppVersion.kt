package ru.kuznetsov.itstime4service.ui

import androidx.compose.runtime.Composable

internal data class AppVersion(val name: String, val buildNumber: String)

@Composable
internal expect fun rememberAppVersion(): AppVersion
