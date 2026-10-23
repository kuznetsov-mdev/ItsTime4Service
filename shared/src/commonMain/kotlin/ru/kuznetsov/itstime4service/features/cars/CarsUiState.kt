package ru.kuznetsov.itstime4service.features.cars

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.indicators.PlateLook

/**
 * State of the "My cars" screen.
 * [isLoading] stays `true` until the first list arrives, so the empty state is not flashed on start.
 * [view] is how the list is shown, as stored in settings.
 * [loadFailed] replaces the list with the load error; [saveFailed] shows the "could not save" bar.
 */
data class CarsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val cars: List<CarItem> = emptyList(),
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val view: CarsView = CarsView.LIST,
)

/**
 * One car of the list.
 * [alertCount] is the number of soon and due tasks shown inside the gauge ring of a row,
 * [gaugeValue] is the filled share of that ring (0..1) and [status] picks its color.
 * A card shows the status ring of [dueCount] and [soonCount] of [taskCount] tasks, up to four [upcomingTasks]
 * (most urgent first), up to four [recentRecords] (newest first) and the licence plate drawn as [plateLook].
 */
data class CarItem(
    val id: String,
    val brand: String,
    val mileageKm: Int,
    val alertCount: Int,
    val gaugeValue: Float,
    val status: TaskStatus,
    val dueCount: Int = 0,
    val soonCount: Int = 0,
    val taskCount: Int = 0,
    val upcomingTasks: List<UpcomingTask> = emptyList(),
    val recentRecords: List<RecentRecord> = emptyList(),
    val plateLook: PlateLook? = null,
    val plateHidden: Boolean = false,
)

/**
 * A task line of a car card: [remainingKm] is negative once overdue, [status] colors it.
 */
data class UpcomingTask(
    val name: String,
    val remainingKm: Int,
    val status: TaskStatus,
)

/**
 * A history line of a car card.
 */
data class RecentRecord(
    val taskName: String,
    val date: LocalDate,
)
