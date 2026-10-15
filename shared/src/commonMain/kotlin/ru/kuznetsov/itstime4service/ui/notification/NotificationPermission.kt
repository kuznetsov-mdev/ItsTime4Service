package ru.kuznetsov.itstime4service.ui.notification

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
