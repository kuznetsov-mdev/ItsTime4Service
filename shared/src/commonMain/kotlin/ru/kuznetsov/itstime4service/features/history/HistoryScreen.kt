package ru.kuznetsov.itstime4service.features.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_entry
import itstime4service.shared.generated.resources.history_empty
import itstime4service.shared.generated.resources.history_nothing_found
import itstime4service.shared.generated.resources.months
import itstime4service.shared.generated.resources.screen_history
import ru.kuznetsov.itstime4service.domain.model.ServiceHistoryEntry
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.Fab
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    initialCarId: String? = null,
    viewModel: HistoryViewModel = koinViewModel { parametersOf(initialCarId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onCarMenuToggle = viewModel::onCarMenuToggle,
        onCarMenuDismiss = viewModel::onCarMenuDismiss,
        onCarSelect = viewModel::onCarSelect,
        onFilterOpen = viewModel::onFilterOpen,
        onFilterChange = viewModel::onFilterChange,
        onFilterApply = viewModel::onFilterApply,
        onFilterReset = viewModel::onFilterReset,
        onFilterDismiss = viewModel::onFilterDismiss,
        onEntryOpen = viewModel::onEntryOpen,
        onEntryChange = viewModel::onEntryChange,
        onEntrySave = viewModel::onEntrySave,
        onEntryDismiss = viewModel::onEntryDismiss,
        onNewEntryOpen = viewModel::onNewEntryOpen,
        onNewEntryChange = viewModel::onNewEntryChange,
        onNewEntrySave = viewModel::onNewEntrySave,
        onNewEntryDismiss = viewModel::onNewEntryDismiss,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onCarMenuToggle: () -> Unit,
    onCarMenuDismiss: () -> Unit,
    onCarSelect: (carId: String?) -> Unit,
    onFilterOpen: () -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onFilterApply: () -> Unit,
    onFilterReset: () -> Unit,
    onFilterDismiss: () -> Unit,
    onEntryOpen: (HistoryRow) -> Unit,
    onEntryChange: (HistoryEntryForm) -> Unit,
    onEntrySave: () -> Unit,
    onEntryDismiss: () -> Unit,
    onNewEntryOpen: () -> Unit,
    onNewEntryChange: (NewHistoryEntryForm) -> Unit,
    onNewEntrySave: () -> Unit,
    onNewEntryDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAddEntry = !state.isLoading && state.activeCars.isNotEmpty()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(title = stringResource(Res.string.screen_history), modifier = Modifier.padding(top = 12.dp))
            HistoryBar(
                state = state,
                onCarMenuToggle = onCarMenuToggle,
                onCarMenuDismiss = onCarMenuDismiss,
                onCarSelect = onCarSelect,
                onFilterOpen = onFilterOpen,
            )
            if (state.years.isEmpty()) {
                if (!state.isLoading) {
                    EmptyHistory(
                        text = stringResource(
                            if (state.hasEntries) Res.string.history_nothing_found else Res.string.history_empty,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = if (canAddEntry) 88.dp else 18.dp),
                ) {
                    state.years.forEach { year ->
                        stickyHeader(key = "year-${year.year}") { YearHeader(year.year) }
                        items(year.months, key = { "month-${year.year}-${it.month}" }) { month ->
                            MonthCard(
                                month = month,
                                showCar = state.selectedCarId == null,
                                distanceUnit = state.distanceUnit,
                                onEntryOpen = onEntryOpen,
                            )
                        }
                    }
                }
            }
        }
        if (canAddEntry) {
            Fab(
                icon = ItsTime4ServiceIcons.Plus,
                onClick = onNewEntryOpen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 22.dp),
                contentDescription = stringResource(Res.string.action_add_entry),
            )
        }
    }
    val filterDraft = state.filterDraft
    if (filterDraft != null) {
        HistoryFilterDialog(
            filter = filterDraft,
            distanceUnit = state.distanceUnit,
            onChange = onFilterChange,
            onApply = onFilterApply,
            onReset = onFilterReset,
            onDismiss = onFilterDismiss,
        )
    }
    val entryForm = state.entryForm
    if (entryForm != null) {
        HistoryEntryDialog(
            form = entryForm,
            distanceUnit = state.distanceUnit,
            onChange = onEntryChange,
            onSave = onEntrySave,
            onDismiss = onEntryDismiss,
        )
    }
    val newEntry = state.newEntry
    if (newEntry != null) {
        NewHistoryEntryDialog(
            form = newEntry,
            cars = state.activeCars,
            suggestions = state.newEntrySuggestions,
            distanceUnit = state.distanceUnit,
            onChange = onNewEntryChange,
            onSave = onNewEntrySave,
            onDismiss = onNewEntryDismiss,
        )
    }
}

private val previewKia = HistoryCar("c1", "Kia Rio")
private val previewLada = HistoryCar("s1", "Lada Granta", isSold = true)

private fun previewRow(id: String, car: HistoryCar, taskName: String, date: LocalDate, mileageKm: Int, costRub: Int?) =
    HistoryRow(ServiceHistoryEntry(id, car.id, taskName, date, mileageKm, costRub, "АвтоСервис Юг", "Масло Motul 5W-30"), car)

private val previewState = HistoryUiState(
    isLoading = false,
    cars = listOf(previewKia, previewLada),
    hasEntries = true,
    years = listOf(
        HistoryYear(
            2026,
            listOf(
                HistoryMonth(
                    1,
                    listOf(
                        previewRow("h4", previewKia, "Замена масла и фильтра", LocalDate(2026, 1, 15), 78_000, 2_400),
                        previewRow("k15", previewKia, "Салонный фильтр", LocalDate(2026, 1, 15), 78_000, 800),
                    ),
                ),
            ),
        ),
        HistoryYear(
            2022,
            listOf(HistoryMonth(11, listOf(previewRow("g3", previewLada, "Замена масла и фильтра", LocalDate(2022, 11, 5), 71_000, 2_000)))),
        ),
    ),
)

@Composable
private fun HistoryPreviewContent() {
    HistoryContent(
        state = previewState,
        onCarMenuToggle = {},
        onCarMenuDismiss = {},
        onCarSelect = {},
        onFilterOpen = {},
        onFilterChange = {},
        onFilterApply = {},
        onFilterReset = {},
        onFilterDismiss = {},
        onEntryOpen = {},
        onEntryChange = {},
        onEntrySave = {},
        onEntryDismiss = {},
        onNewEntryOpen = {},
        onNewEntryChange = {},
        onNewEntrySave = {},
        onNewEntryDismiss = {},
    )
}

@Preview
@Composable
private fun HistoryContentLightPreview() = ScreenPreview(darkTheme = false) {
    HistoryPreviewContent()
}

@Preview
@Composable
private fun HistoryContentDarkPreview() = ScreenPreview(darkTheme = true) {
    HistoryPreviewContent()
}
