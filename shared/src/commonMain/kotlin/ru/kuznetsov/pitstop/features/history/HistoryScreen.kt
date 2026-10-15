package ru.kuznetsov.pitstop.features.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_filter
import pitstop.shared.generated.resources.action_select_car
import pitstop.shared.generated.resources.cost_rub
import pitstop.shared.generated.resources.history_all_cars
import pitstop.shared.generated.resources.history_empty
import pitstop.shared.generated.resources.history_nothing_found
import pitstop.shared.generated.resources.months
import pitstop.shared.generated.resources.screen_history
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.ServiceHistoryEntry
import ru.kuznetsov.pitstop.ui.components.ScreenPreview
import ru.kuznetsov.pitstop.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTitle
import ru.kuznetsov.pitstop.ui.format.distanceText
import ru.kuznetsov.pitstop.ui.format.grouped
import ru.kuznetsov.pitstop.ui.format.toDisplayText
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

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
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
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
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp)) {
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
}

@Composable
private fun HistoryBar(
    state: HistoryUiState,
    onCarMenuToggle: () -> Unit,
    onCarMenuDismiss: () -> Unit,
    onCarSelect: (carId: String?) -> Unit,
    onFilterOpen: () -> Unit,
) {
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusM)
    val selectedCar = state.selectedCar
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).onSizeChanged { fieldWidthPx = it.width }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface2)
                    .border(1.dp, colors.rule, shape)
                    .clickable(onClickLabel = stringResource(Res.string.action_select_car), onClick = onCarMenuToggle)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedCar?.label() ?: stringResource(Res.string.history_all_cars),
                    style = typography.sans.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = if (selectedCar?.isSold == true) colors.inkFaint else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    PitStopIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.inkFaint,
                    modifier = Modifier.size(14.dp).rotate(if (state.isCarMenuOpen) 180f else 0f),
                )
            }
            DropdownMenu(
                expanded = state.isCarMenuOpen,
                onDismissRequest = onCarMenuDismiss,
                containerColor = colors.surface,
                modifier = Modifier.width(with(LocalDensity.current) { fieldWidthPx.toDp() }),
            ) {
                CarMenuItem(
                    label = stringResource(Res.string.history_all_cars),
                    selected = selectedCar == null,
                    sold = false,
                    onClick = { onCarSelect(null) },
                )
                state.cars.forEach { car ->
                    CarMenuItem(
                        label = car.label(),
                        selected = car.id == state.selectedCarId,
                        sold = car.isSold,
                        onClick = { onCarSelect(car.id) },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(shape)
                .background(colors.surface2)
                .border(1.dp, if (state.filter.isActive) colors.accent else colors.rule, shape)
                .clickable(onClick = onFilterOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PitStopIcons.Filter,
                contentDescription = stringResource(Res.string.action_filter),
                tint = if (state.filter.isActive) colors.accent else colors.inkMuted,
                modifier = Modifier.size(17.dp),
            )
            if (state.filter.isActive) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
    }
}

@Composable
private fun CarMenuItem(label: String, selected: Boolean, sold: Boolean, onClick: () -> Unit) {
    val colors = PitStopTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = PitStopTheme.typography.sans.copy(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = when {
                    sold -> colors.inkFaint
                    selected -> colors.accent
                    else -> colors.ink
                },
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun YearHeader(year: Int) {
    val colors = PitStopTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.bg).padding(top = 10.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
        Text(
            year.toString(),
            style = PitStopTheme.typography.display.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = colors.inkMuted,
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
    }
}

@Composable
private fun MonthCard(
    month: HistoryMonth,
    showCar: Boolean,
    distanceUnit: DistanceUnit,
    onEntryOpen: (HistoryRow) -> Unit,
) {
    val colors = PitStopTheme.colors
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusL)
    val chipShape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, bottom = 12.dp)) {
        Column(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.rule, shape)
                .padding(top = 8.dp),
        ) {
            month.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(colors.rule))
                }
                HistoryRowItem(row = row, showCar = showCar, distanceUnit = distanceUnit, onClick = { onEntryOpen(row) })
            }
        }
        Text(
            stringArrayResource(Res.array.months)[month.month - 1].uppercase(),
            style = PitStopTheme.typography.mono.copy(
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.06f.em,
            ),
            color = colors.inkFaint,
            modifier = Modifier
                .offset(x = 14.dp)
                .clip(chipShape)
                .background(colors.surface)
                .border(1.dp, colors.rule, chipShape)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun HistoryRowItem(row: HistoryRow, showCar: Boolean, distanceUnit: DistanceUnit, onClick: () -> Unit) {
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val entry = row.entry
    val metaStyle = typography.mono.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal)
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                entry.taskName,
                style = typography.sans.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(entry.date.toDisplayText(), style = metaStyle, color = colors.inkFaint, modifier = Modifier.alignByBaseline())
        }
        val meta = listOfNotNull(
            distanceText(entry.mileageKm, distanceUnit),
            entry.costRub?.let { stringResource(Res.string.cost_rub, it.grouped()) },
            entry.serviceName,
        ).joinToString("   ")
        if (showCar) {
            Text(
                row.car.label(),
                style = typography.sans.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                color = if (row.car.isSold) colors.inkFaint else colors.ink,
            )
        }
        Text(meta, style = metaStyle, color = colors.inkMuted)
        if (entry.comment != null) {
            Text(
                entry.comment,
                style = typography.sans.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun EmptyHistory(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = PitStopTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = PitStopTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
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
