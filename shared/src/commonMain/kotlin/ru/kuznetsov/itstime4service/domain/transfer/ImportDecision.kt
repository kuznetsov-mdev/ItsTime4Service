package ru.kuznetsov.itstime4service.domain.transfer

enum class ImportMode { REPLACE_BACKUP, MERGE_BACKUP, INTO_CAR }

sealed interface TargetCar {
    data class Existing(val localCarId: String) : TargetCar
    data class New(val brand: String, val confirmedMileageKm: Int) : TargetCar
}

enum class TaskChoice { SKIP, UPDATE_PARAMETERS, ADD_SEPARATE }
enum class PersonalChoice { KEEP_LOCAL, TAKE_INCOMING }
enum class BackupSafety { NOT_REQUIRED, SAVED, EXPLICITLY_DECLINED }

data class EntityRef(val carId: String, val entityId: String)

data class ImportDecisions(
    val carTargets: Map<String, TargetCar> = emptyMap(),
    val selectedTasks: Set<EntityRef>,
    val taskChoices: Map<EntityRef, TaskChoice> = emptyMap(),
    val taskTargets: Map<EntityRef, String> = emptyMap(),
    val personalChoices: Map<String, PersonalChoice> = emptyMap(),
    val relatedChoices: Map<String, PersonalChoice> = emptyMap(),
    val soldChoices: Map<String, PersonalChoice> = emptyMap(),
    val restoreSettings: Boolean = false,
    val backupSafety: BackupSafety = BackupSafety.NOT_REQUIRED,
) {
    companion object {
        fun initial(snapshot: TransferSnapshot): ImportDecisions = ImportDecisions(
            selectedTasks = snapshot.cars.flatMap { car -> car.tasks.map { EntityRef(car.id, it.id) } }.toSet(),
        )
    }
}

data class ImportRequest(
    val snapshot: TransferSnapshot,
    val snapshotDigest: String,
    val mode: ImportMode,
    val decisions: ImportDecisions,
)

enum class ImportDecisionReason {
    CAR_TARGET, MISSING_ENTITY, TASK_TARGET, DUPLICATE_TARGET, PERSONAL_FIELD,
    RELATED_SERVICE_HISTORY, SOLD_STATE, BACKUP_SAFETY, IDENTITY_COLLISION,
}

data class PendingDecision(
    val id: String,
    val affected: Set<EntityRef>,
    val reason: ImportDecisionReason,
    val field: String? = null,
    val candidates: List<TransferTask> = emptyList(),
)
