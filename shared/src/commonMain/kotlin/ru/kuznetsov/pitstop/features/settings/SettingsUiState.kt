package ru.kuznetsov.pitstop.features.settings

import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.ui.format.kmIn
import kotlin.math.roundToInt

/**
 * State of the settings screen.
 * [settings] is `null` only until the stored settings arrive.
 * [isPickingReminderTime] is `true` while the reminder time dialog is open.
 * [cars] feeds the "open by default" list and the "delete a car" list; both groups are hidden without cars.
 * [openDefault] and [defaultCarId] describe the "open by default" choice; [purgeCar] is the car whose
 * full deletion is being confirmed.
 */
data class SettingsUiState(
    val settings: Settings? = null,
    val isPickingReminderTime: Boolean = false,
    val cars: List<SettingsCar> = emptyList(),
    val openDefault: Boolean = false,
    val defaultCarId: String? = null,
    val purgeCar: SettingsCar? = null,
) {
    val reminderSteps: Int
        get() {
            val settings = settings ?: return MIN_REMINDER_STEPS
            val inUnit = settings.defaultReminderWindowKm.kmIn(settings.distanceUnit)
            return (inUnit / KM_PER_STEP.toDouble()).roundToInt().coerceIn(MIN_REMINDER_STEPS, MAX_REMINDER_STEPS)
        }

    companion object {
        const val KM_PER_STEP = 1_000
        const val MIN_REMINDER_STEPS = 1
        const val MAX_REMINDER_STEPS = 9
    }
}

/**
 * A car as listed in settings.
 * [isSold] marks a car that was deleted while its history was kept.
 */
data class SettingsCar(
    val id: String,
    val brand: String,
    val mileageKm: Int,
    val isSold: Boolean = false,
)
