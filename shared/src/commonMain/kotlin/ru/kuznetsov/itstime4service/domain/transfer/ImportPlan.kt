package ru.kuznetsov.itstime4service.domain.transfer

import ru.kuznetsov.itstime4service.domain.model.Settings

data class ImportSummary(
    val carsAdded: Int, val carsUpdated: Int, val carsRemoved: Int,
    val tasksAdded: Int, val tasksUpdated: Int, val tasksRemoved: Int,
    val historyAdded: Int, val historyUpdated: Int, val historyRemoved: Int,
    val skipped: Int, val settingsRestored: Boolean,
    val rollbackDisabled: Set<EntityRef>,
)

sealed interface PreparedImport {
    data class NeedsDecisions(val decisions: List<PendingDecision>) : PreparedImport
    data class Ready(val plan: ImportPlan) : PreparedImport
}

class ImportPlan internal constructor(
    val operationId: String,
    val snapshotId: String,
    val snapshotDigest: String,
    val databaseDigest: String,
    val decisionsDigest: String,
    val mode: ImportMode,
    summary: ImportSummary,
    internal val validatedChanges: ImportChangeSet,
    internal val changesDigest: String,
) {
    private val capturedSummary = summary.copy(rollbackDisabled = summary.rollbackDisabled.toSet())
    val summary: ImportSummary get() = capturedSummary.copy(rollbackDisabled = capturedSummary.rollbackDisabled.toSet())
    val preview: ImportPreview get() = ImportPreview(
        validatedChanges.cars.map { car -> car.copy(tasks = car.tasks.toList(), history = Presence.Present(car.history.required().toList())) },
        validatedChanges.carTargets.toMap(), validatedChanges.taskTargets.toMap(), validatedChanges.settings,
    )
}

data class ImportPreview(
    val cars: List<TransferCar>,
    val carTargets: Map<String, String>,
    val taskTargets: Map<EntityRef, TransferTask>,
    val settings: Settings,
)

enum class NotificationState { PENDING, APPLIED, RETRY_REQUIRED }

data class ImportResult(
    val operationId: String,
    val summary: ImportSummary,
    val alreadyApplied: Boolean,
    val notifications: NotificationState,
)

internal data class ImportProvenance(
    val snapshotId: String,
    val sourceDigest: String,
    val kind: String,
    val incomingId: String,
    val targetCarId: String,
    val localId: String,
    val instanceId: String,
)

internal data class ImportState(
    val cars: List<TransferCar>,
    val settings: Settings,
    val reminders: List<TransferReminder>,
    val provenance: List<ImportProvenance>,
)

internal data class ImportChangeSet(
    val cars: List<TransferCar>,
    val settings: Settings,
    val reminders: List<TransferReminder>,
    val provenance: List<ImportProvenance>,
    val carTargets: Map<String, String>,
    val taskTargets: Map<EntityRef, TransferTask>,
)
