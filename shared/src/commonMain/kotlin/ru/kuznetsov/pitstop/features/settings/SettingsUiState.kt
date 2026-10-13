package ru.kuznetsov.pitstop.features.settings

import ru.kuznetsov.pitstop.domain.model.Settings

/**
 * State of the settings screen.
 * [settings] is `null` only until the stored settings arrive.
 * [isPickingReminderTime] is `true` while the reminder time dialog is open.
 */
data class SettingsUiState(
    val settings: Settings? = null,
    val isPickingReminderTime: Boolean = false,
)
