package ru.kuznetsov.itstime4service.features.cars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.cars_count
import itstime4service.shared.generated.resources.cars_empty
import itstime4service.shared.generated.resources.cars_gauge_hint
import itstime4service.shared.generated.resources.screen_cars
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.cards.ListRowCard
import ru.kuznetsov.itstime4service.ui.components.indicators.ErrorBar
import ru.kuznetsov.itstime4service.ui.components.indicators.GaugeRing
import ru.kuznetsov.itstime4service.ui.components.indicators.Preloader
import ru.kuznetsov.itstime4service.ui.components.indicators.color
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.components.screen.LoadError
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.components.screen.isCompactHeight
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.toTier
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun CarsScreen(
    onOpenCar: (carId: String) -> Unit,
    onOpenHistory: (carId: String) -> Unit,
    onAddCar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CarsContent(
        state = state,
        onViewSelect = viewModel::onViewSelect,
        onPlateToggle = viewModel::onPlateToggle,
        onOpenCar = onOpenCar,
        onOpenHistory = onOpenHistory,
        onAddCar = onAddCar,
        modifier = modifier,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        onRetry = viewModel::onRetry,
    )
}

@Composable
internal fun CarsContent(
    state: CarsUiState,
    onViewSelect: (CarsView) -> Unit,
    onPlateToggle: (carId: String) -> Unit,
    onOpenCar: (carId: String) -> Unit,
    onOpenHistory: (carId: String) -> Unit,
    onAddCar: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveFailureShown: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        CarsList(state, onViewSelect, onPlateToggle, onOpenCar, onOpenHistory, onAddCar, onRetry)
        ErrorBar(
            visible = state.saveFailed,
            onShown = onSaveFailureShown,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CarsList(
    state: CarsUiState,
    onViewSelect: (CarsView) -> Unit,
    onPlateToggle: (carId: String) -> Unit,
    onOpenCar: (carId: String) -> Unit,
    onOpenHistory: (carId: String) -> Unit,
    onAddCar: () -> Unit,
    onRetry: () -> Unit,
) {
    val isList = state.view == CarsView.LIST
    val count = pluralStringResource(Res.plurals.cars_count, state.cars.size, state.cars.size)
    val subtitle = if (isList) "$count · ${stringResource(Res.string.cars_gauge_hint)}" else null
    val header: @Composable () -> Unit = {
        Column {
            CarsViewToggle(
                selected = state.view,
                onSelect = onViewSelect,
                modifier = Modifier.padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 12.dp),
            )
            ScreenTitle(title = stringResource(Res.string.screen_cars), subtitle = subtitle)
        }
    }
    val headerScrolls = isCompactHeight() && !state.isLoading && !state.loadFailed && state.cars.isNotEmpty()
    val side = if (isList) 14.dp else 18.dp
    Column(Modifier.fillMaxSize()) {
        if (!headerScrolls) header()
        if (state.isLoading) {
            Preloader(Modifier.weight(1f).fillMaxWidth())
        } else if (state.loadFailed) {
            LoadError(onRetry = onRetry, modifier = Modifier.weight(1f).fillMaxWidth())
        } else if (state.cars.isEmpty()) {
            EmptyCars(Modifier.weight(1f))
            AddCarRow(onClick = onAddCar, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = if (isList) PaddingValues(top = 14.dp, bottom = 14.dp) else PaddingValues(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isList) 10.dp else 12.dp),
            ) {
                if (headerScrolls) item(key = HEADER_KEY) { header() }
                items(state.cars, key = { it.id }) { car ->
                    if (isList) {
                        ListRowCard(
                            icon = ItsTime4ServiceIcons.Car,
                            title = car.brand,
                            subtitle = distanceText(car.mileageKm, state.distanceUnit),
                            onClick = { onOpenCar(car.id) },
                            modifier = Modifier.padding(horizontal = side).fillMaxWidth(),
                            trailing = {
                                GaugeRing(
                                    value = car.gaugeValue,
                                    color = car.status.toTier().color,
                                    count = car.alertCount,
                                )
                            },
                        )
                    } else {
                        CarCard(
                            car = car,
                            withHistory = state.view == CarsView.TASKS_AND_HISTORY,
                            distanceUnit = state.distanceUnit,
                            onOpenCar = { onOpenCar(car.id) },
                            onOpenHistory = { onOpenHistory(car.id) },
                            onPlateToggle = { onPlateToggle(car.id) },
                            modifier = Modifier.padding(horizontal = side),
                        )
                    }
                }
                item(key = ADD_CAR_KEY) {
                    AddCarRow(onClick = onAddCar, modifier = Modifier.padding(horizontal = side), minHeight = if (isList) 66.dp else 54.dp)
                }
            }
        }
    }
}

@Composable
private fun EmptyCars(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.cars_empty),
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = ItsTime4ServiceTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewState = CarsUiState(
    isLoading = false,
    cars = listOf(
        CarItem(
            "c1", "Kia Rio", 84_300, 1, 0.25f, TaskStatus.SOON,
            soonCount = 1, taskCount = 4,
            upcomingTasks = listOf(
                UpcomingTask("Engine oil", 1_200, TaskStatus.SOON),
                UpcomingTask("Air filter", 6_700, TaskStatus.OK),
            ),
            recentRecords = listOf(RecentRecord("Brake pads", LocalDate(2026, 8, 14))),
        ),
        CarItem(
            "c2", "Toyota Camry", 152_000, 2, 0.67f, TaskStatus.DUE,
            dueCount = 1, soonCount = 1, taskCount = 3,
            upcomingTasks = listOf(
                UpcomingTask("Timing belt", -800, TaskStatus.DUE),
                UpcomingTask("Spark plugs", 900, TaskStatus.SOON),
            ),
        ),
        CarItem("c3", "Hyundai Solaris", 23_100, 0, 0.06f, TaskStatus.OK),
    ),
    distanceUnit = DistanceUnit.KILOMETERS,
)

@Preview
@Composable
private fun CarsContentLightPreview() = ScreenPreview(darkTheme = false) {
    CarsContent(state = previewState, onViewSelect = {}, onPlateToggle = {}, onOpenCar = {}, onOpenHistory = {}, onAddCar = {})
}

@Preview
@Composable
private fun CarsContentDarkPreview() = ScreenPreview(darkTheme = true) {
    CarsContent(state = previewState, onViewSelect = {}, onPlateToggle = {}, onOpenCar = {}, onOpenHistory = {}, onAddCar = {})
}

@Preview
@Composable
private fun CarsCardsLightPreview() = ScreenPreview(darkTheme = false) {
    CarsContent(
        state = previewState.copy(view = CarsView.TASKS_AND_HISTORY),
        onViewSelect = {},
        onPlateToggle = {},
        onOpenCar = {},
        onOpenHistory = {},
        onAddCar = {},
    )
}


@Preview
@Composable
private fun CarsCardsDarkPreview() = ScreenPreview(darkTheme = true) {
    CarsContent(
        state = previewState.copy(view = CarsView.TASKS_AND_HISTORY),
        onViewSelect = {},
        onPlateToggle = {},
        onOpenCar = {},
        onOpenHistory = {},
        onAddCar = {},
    )
}

private const val ADD_CAR_KEY = "add_car"
private const val HEADER_KEY = "header"
