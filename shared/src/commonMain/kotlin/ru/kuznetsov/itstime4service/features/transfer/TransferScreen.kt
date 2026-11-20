package ru.kuznetsov.itstime4service.features.transfer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransferScreen(
    entry: TransferEntry,
    onClose: (imported: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = koinViewModel { parametersOf(entry) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    LaunchedEffect(state.isClosed) { if (state.isClosed) onClose(state.importResult != null) }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = viewModel::onBack)
    val actions = remember(viewModel) {
        TransferActions(viewModel::onBack, viewModel::onRetryLoad, viewModel::onChooseCarExport, viewModel::onStartExport,
            viewModel::onExportCarSelect, viewModel::onExportTask, viewModel::onExportAllTasks,
            viewModel::onDescription, viewModel::onExportGroup, viewModel::onProtectionChange,
            viewModel::onConfirmPlaintext, viewModel::onPrepareExport, viewModel::onWriteExport,
            viewModel::onStartImport, viewModel::onPickFile, viewModel::onUnlock,
            viewModel::onTargetDraft, viewModel::onConfirmTarget, viewModel::onImportMode,
            viewModel::onRestoreSettings, viewModel::onImportTask, viewModel::onTaskChoice,
            viewModel::onTaskTarget, viewModel::onPersonalChoice, viewModel::onSaveSafety,
            viewModel::onDeclineBackup, viewModel::onPrepareImport, viewModel::onApplyImport,
            viewModel::onRetryNotifications)
    }
    TransferContent(state, actions, modifier)
}
