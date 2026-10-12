package ru.kuznetsov.pitstop.features.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import org.jetbrains.compose.resources.stringResource
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.tab_cars
import pitstop.shared.generated.resources.tab_settings
import ru.kuznetsov.pitstop.features.cardetail.CarDetailScreen
import ru.kuznetsov.pitstop.features.carform.CarFormScreen
import ru.kuznetsov.pitstop.features.cars.CarsScreen
import ru.kuznetsov.pitstop.features.history.HistoryScreen
import ru.kuznetsov.pitstop.features.settings.SettingsScreen
import ru.kuznetsov.pitstop.ui.components.buttons.TabBarItem
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

/** Root of the app — hosts every destination and shows the bottom tab bar on the two top-level ones. */
@Composable
fun PitStopNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val carIdArgument = listOf(navArgument(Destination.ARG_CAR_ID) { type = NavType.StringType })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute == Destination.Cars.route || currentRoute == Destination.Settings.route) {
                TabBar(currentRoute = currentRoute, onSelect = navController::navigateToTab)
            }
        },
        containerColor = PitStopTheme.colors.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Cars.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Cars.route) {
                CarsScreen(
                    onOpenCar = { carId -> navController.navigate(Destination.CarDetail.createRoute(carId)) },
                    onAddCar = { navController.navigate(Destination.AddCar.route) },
                )
            }
            composable(Destination.AddCar.route) {
                CarFormScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.CarDetail.route, arguments = carIdArgument) { entry ->
                val carId = entry.carId
                CarDetailScreen(
                    carId = carId,
                    onBack = { navController.popBackStack(Destination.Cars.route, inclusive = false) },
                    onOpenHistory = { navController.navigate(Destination.History.createRoute(carId)) },
                    onEditCar = { navController.navigate(Destination.EditCar.createRoute(carId)) },
                )
            }
            composable(Destination.EditCar.route, arguments = carIdArgument) { entry ->
                CarFormScreen(onBack = { navController.popBackStack() }, carId = entry.carId)
            }
            composable(Destination.History.route, arguments = carIdArgument) { entry ->
                HistoryScreen(carId = entry.carId, onBack = { navController.popBackStack() })
            }
            composable(Destination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun TabBar(currentRoute: String, onSelect: (Destination) -> Unit) {
    val colors = PitStopTheme.colors
    Column(
        Modifier
            .background(colors.surface)
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.rule),
        )
        Row {
            TabBarItem(
                icon = PitStopIcons.Car,
                label = stringResource(Res.string.tab_cars),
                selected = currentRoute == Destination.Cars.route,
                onClick = { onSelect(Destination.Cars) },
                modifier = Modifier.weight(1f),
            )
            TabBarItem(
                icon = PitStopIcons.Gear,
                label = stringResource(Res.string.tab_settings),
                selected = currentRoute == Destination.Settings.route,
                onClick = { onSelect(Destination.Settings) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun NavHostController.navigateToTab(destination: Destination) {
    navigate(destination.route) {
        popUpTo(Destination.Cars.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val NavBackStackEntry.carId: String
    get() = arguments?.read { getString(Destination.ARG_CAR_ID) }.orEmpty()
