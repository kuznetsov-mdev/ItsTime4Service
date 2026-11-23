package ru.kuznetsov.itstime4service.features.transfer

import ru.kuznetsov.itstime4service.domain.model.Car
import ru.kuznetsov.itstime4service.domain.model.CarSummary
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.transfer.*

enum class TransferStep { HUB, EXPORT_PURPOSE, EXPORT, EXPORT_PREVIEW, IMPORT_FILE, UNLOCK, IMPORT_SETUP, IMPORT_DECISIONS, IMPORT_PREVIEW, RESULT }
enum class TransferExportPurpose { FULL_BACKUP, CAR_BACKUP, SHARE_SCHEDULE }
enum class TransferFormProblem {
    PASSWORD_REQUIRED, PASSWORD_MISMATCH, PASSWORD_INVALID, CAR_REQUIRED,
    TARGET_REQUIRED, DUPLICATE_CAR_TARGET, BRAND_REQUIRED, MILEAGE_REQUIRED,
}

sealed interface TransferEntry {
    data object Settings : TransferEntry
    data object CarForm : TransferEntry
    data class CarDetail(val carId: String) : TransferEntry
}

data class ImportTargetDraft(
    val isNew: Boolean = false,
    val existingId: String? = null,
    val brand: String = "",
    val mileage: String = "",
) {
    val hasTarget: Boolean get() = isNew || existingId != null
}

data class TransferUiState(
    val step: TransferStep = TransferStep.HUB,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isBusy: Boolean = false,
    val isClosed: Boolean = false,
    val cars: List<Car> = emptyList(),
    val allCars: List<CarSummary> = emptyList(),
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val exportPurpose: TransferExportPurpose = TransferExportPurpose.FULL_BACKUP,
    val isExportCarFixed: Boolean = false,
    val exportCarId: String? = null,
    val exportTaskIds: Set<String> = emptySet(),
    val exportGroups: Set<TransferGroup> = emptySet(),
    val description: String = "",
    val isProtected: Boolean = true,
    val isConfirmingPlaintext: Boolean = false,
    val exportPreview: TransferSnapshot? = null,
    val incoming: TransferSnapshot? = null,
    val localDetails: List<TransferCar> = emptyList(),
    val importMode: ImportMode = ImportMode.MERGE_BACKUP,
    val decisions: ImportDecisions = ImportDecisions(selectedTasks = emptySet()),
    val targetDrafts: Map<String, ImportTargetDraft> = emptyMap(),
    val pending: List<PendingDecision> = emptyList(),
    val importSummary: ImportSummary? = null,
    val importPreview: ImportPreview? = null,
    val importResult: ImportResult? = null,
    val exportShared: Boolean = false,
    val exportSucceeded: Boolean = false,
    val failure: TransferFailure? = null,
    val problem: TransferFormProblem? = null,
) {
    val isApplyingImport: Boolean get() = step == TransferStep.IMPORT_PREVIEW && isBusy
    val isFullBackup: Boolean get() = exportPurpose == TransferExportPurpose.FULL_BACKUP
    val hasPersonalExport: Boolean get() = isFullBackup || exportGroups.isNotEmpty()
    val exportCar: Car? get() = cars.firstOrNull { it.id == exportCarId }
}
