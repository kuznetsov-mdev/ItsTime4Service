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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_car
import itstime4service.shared.generated.resources.cars_count
import itstime4service.shared.generated.resources.cars_empty
import itstime4service.shared.generated.resources.cars_gauge_hint
import itstime4service.shared.generated.resources.screen_cars
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.toTier
import ru.kuznetsov.itstime4service.ui.components.buttons.Fab
import ru.kuznetsov.itstime4service.ui.components.cards.ListRowCard
import ru.kuznetsov.itstime4service.ui.components.indicators.GaugeRing
import ru.kuznetsov.itstime4service.ui.components.indicators.color
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun CarsScreen(
    onOpenCar: (carId: String) -> Unit,
    onAddCar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CarsContent(state = state, onOpenCar = onOpenCar, onAddCar = onAddCar, modifier = modifier)
}

@Composable
internal fun CarsContent(
    state: CarsUiState,
    onOpenCar: (carId: String) -> Unit,
    onAddCar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val count = pluralStringResource(Res.plurals.cars_count, state.cars.size, state.cars.size)
            ScreenTitle(
                title = stringResource(Res.string.screen_cars),
                subtitle = "$count · ${stringResource(Res.string.cars_gauge_hint)}",
                modifier = Modifier.padding(top = 12.dp),
            )
            if (state.cars.isEmpty() && !state.isLoading) {
                EmptyCars(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.cars, key = { it.id }) { car ->
                        ListRowCard(
                            icon = ItsTime4ServiceIcons.Car,
                            title = car.brand,
                            subtitle = distanceText(car.mileageKm, state.distanceUnit),
                            onClick = { onOpenCar(car.id) },
                            modifier = Modifier.fillMaxWidth(),
                            trailing = {
                                GaugeRing(
                                    value = car.gaugeValue,
                                    color = car.status.toTier().color,
                                    count = car.alertCount,
                                )
                            },
                        )
                    }
                }
            }
        }
        Fab(
            icon = ItsTime4ServiceIcons.Plus,
            onClick = onAddCar,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp),
            contentDescription = stringResource(Res.string.action_add_car),
        )
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
        CarItem("c1", "Kia Rio", 84_300, 1, 0.25f, TaskStatus.SOON),
        CarItem("c2", "Toyota Camry", 152_000, 2, 0.67f, TaskStatus.DUE),
        CarItem("c3", "Hyundai Solaris", 23_100, 0, 0.06f, TaskStatus.OK),
    ),
    distanceUnit = DistanceUnit.KILOMETERS,
)

@Preview
@Composable
private fun CarsContentLightPreview() = ScreenPreview(darkTheme = false) {
    CarsContent(state = previewState, onOpenCar = {}, onAddCar = {})
}

@Preview
@Composable
private fun CarsContentDarkPreview() = ScreenPreview(darkTheme = true) {
    CarsContent(state = previewState, onOpenCar = {}, onAddCar = {})
}
