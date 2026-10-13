package ru.kuznetsov.pitstop.ui.notification

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
