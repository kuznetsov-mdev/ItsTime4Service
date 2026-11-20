package ru.kuznetsov.itstime4service.features.transfer

import ru.kuznetsov.itstime4service.domain.transfer.*

internal class TransferActions(
    val back: () -> Unit = {},
    val retry: () -> Unit = {},
    val chooseCarExport: () -> Unit = {},
    val export: (TransferExportPurpose) -> Unit = {},
    val car: (String) -> Unit = {},
    val exportTask: (String, Boolean) -> Unit = { _, _ -> },
    val allTasks: (Boolean) -> Unit = {},
    val description: (String) -> Unit = {},
    val group: (TransferGroup, Boolean) -> Unit = { _, _ -> },
    val protection: (Boolean) -> Unit = {},
    val plaintext: (Boolean) -> Unit = {},
    val previewExport: (String, String) -> Unit = { _, _ -> },
    val write: (Boolean) -> Unit = {},
    val startImport: () -> Unit = {},
    val pick: () -> Unit = {},
    val unlock: (String) -> Unit = {},
    val target: (String, ImportTargetDraft) -> Unit = { _, _ -> },
    val mode: (ImportMode) -> Unit = {},
    val settings: (Boolean) -> Unit = {},
    val importTask: (EntityRef, Boolean) -> Unit = { _, _ -> },
    val taskChoice: (EntityRef, TaskChoice) -> Unit = { _, _ -> },
    val taskTarget: (EntityRef, String) -> Unit = { _, _ -> },
    val personal: (PendingDecision, PersonalChoice) -> Unit = { _, _ -> },
    val safety: (String, String) -> Unit = { _, _ -> },
    val declineSafety: () -> Unit = {},
    val previewImport: () -> Unit = {},
    val apply: () -> Unit = {},
    val retryNotifications: () -> Unit = {},
)
