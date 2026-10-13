package ru.kuznetsov.pitstop.ui.format

import org.jetbrains.compose.resources.StringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.preset_air_filter
import pitstop.shared.generated.resources.preset_oil
import pitstop.shared.generated.resources.preset_pads
import pitstop.shared.generated.resources.preset_timing_belt
import pitstop.shared.generated.resources.task_name_air_filter
import pitstop.shared.generated.resources.task_name_oil
import pitstop.shared.generated.resources.task_name_pads
import pitstop.shared.generated.resources.task_name_timing_belt

/**
 * Ready-made tasks behind the quick-add chips, with the intervals used by the design mockup.
 * A preset fills the name, the interval and the reminder window; the last-done mileage is left
 * for the user. The task name is localized, so it is supplied by the screen.
 */
enum class TaskPreset(val intervalKm: Int, val reminderWindowKm: Int) {
    OIL(10_000, 1_500),
    PADS(30_000, 3_000),
    AIR_FILTER(15_000, 2_000),
    TIMING_BELT(60_000, 5_000),
    ;

    fun toFields(name: String, current: TaskFields = TaskFields()): TaskFields = current.copy(
        name = name,
        intervalKm = intervalKm.toString(),
        reminderWindowKm = reminderWindowKm.toString(),
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
