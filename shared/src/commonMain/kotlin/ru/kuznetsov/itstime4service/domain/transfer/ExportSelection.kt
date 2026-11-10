package ru.kuznetsov.itstime4service.domain.transfer

sealed interface ExportSelection {
    data object FullBackup : ExportSelection

    data class Car(
        val carId: String,
        val taskIds: Set<String>,
        val personalGroups: Set<TransferGroup> = emptySet(),
        val maintenanceDescription: String? = null,
    ) : ExportSelection {
        internal fun isValid(): Boolean = personalGroups.all { group ->
            when (group) {
                TransferGroup.MILEAGE, TransferGroup.VIN, TransferGroup.PLATE,
                TransferGroup.LAST_SERVICE, TransferGroup.HISTORY, TransferGroup.HISTORY_DETAILS -> true
                TransferGroup.PREFERENCES, TransferGroup.ROLLBACK, TransferGroup.REMINDER_STATE -> false
            }
        } && (TransferGroup.HISTORY_DETAILS !in personalGroups || TransferGroup.HISTORY in personalGroups)
    }
}
