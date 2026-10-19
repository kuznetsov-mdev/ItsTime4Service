package ru.kuznetsov.itstime4service.ui.format

import org.jetbrains.compose.resources.StringResource
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.preset_air_filter
import itstime4service.shared.generated.resources.preset_oil
import itstime4service.shared.generated.resources.preset_pads
import itstime4service.shared.generated.resources.preset_timing_belt
import itstime4service.shared.generated.resources.task_name_air_filter
import itstime4service.shared.generated.resources.task_name_oil
import itstime4service.shared.generated.resources.task_name_pads
import itstime4service.shared.generated.resources.task_name_timing_belt

/**
 * Ready-made tasks behind the quick-add chips, with the intervals used by the design mockup.
 * A preset fills the name, the interval and the reminder window; the last-done mileage is left
 * for the user. The task name is localized, so it is supplied by the screen. The intervals are in kilometers
 * and are converted to the unit chosen in settings when they fill a form.
 */
enum class TaskPreset(val intervalKm: Int, val reminderWindowKm: Int) {
    OIL(7_500, 1_500),
    PADS(30_000, 3_000),
    AIR_FILTER(15_000, 2_000),
    TIMING_BELT(60_000, 5_000),
    ;

    fun toFields(name: String, unit: DistanceUnit, current: TaskFields = TaskFields()): TaskFields = current.copy(
        name = name,
        intervalKm = intervalKm.kmIn(unit).toString(),
        reminderWindowKm = reminderWindowKm.kmIn(unit).toString(),
    )
}

val TaskPreset.chipLabel: StringResource
    get() = when (this) {
        TaskPreset.OIL -> Res.string.preset_oil
        TaskPreset.PADS -> Res.string.preset_pads
        TaskPreset.AIR_FILTER -> Res.string.preset_air_filter
        TaskPreset.TIMING_BELT -> Res.string.preset_timing_belt
    }

val TaskPreset.taskName: StringResource
    get() = when (this) {
        TaskPreset.OIL -> Res.string.task_name_oil
        TaskPreset.PADS -> Res.string.task_name_pads
        TaskPreset.AIR_FILTER -> Res.string.task_name_air_filter
        TaskPreset.TIMING_BELT -> Res.string.task_name_timing_belt
    }
