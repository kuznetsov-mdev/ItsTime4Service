package ru.kuznetsov.itstime4service.ui.components.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

@Composable
fun isCompactHeight(): Boolean = LocalWindowInfo.current.containerDpSize.height < COMPACT_HEIGHT

private val COMPACT_HEIGHT = 460.dp
