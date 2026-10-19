package ru.kuznetsov.itstime4service.features.navigation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.tab_cars
import itstime4service.shared.generated.resources.tab_history
import itstime4service.shared.generated.resources.tab_settings
import ru.kuznetsov.itstime4service.features.cardetail.CarDetailScreen
import ru.kuznetsov.itstime4service.features.carform.CarFormScreen
import ru.kuznetsov.itstime4service.features.cars.CarsScreen
import ru.kuznetsov.itstime4service.features.history.HistoryScreen
import ru.kuznetsov.itstime4service.features.licenses.LicensesScreen
import ru.kuznetsov.itstime4service.features.settings.SettingsScreen
import ru.kuznetsov.itstime4service.features.taskform.TaskFormScreen
import ru.kuznetsov.itstime4service.ui.components.buttons.TabBarItem
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/**
 * Root of the app — hosts every destination and shows the bottom tab bar on the top-level ones.
 * The app starts on the car list; with a [launchCarId] that car's screen opens over the list once, so back
 * leads to the list. Turning the screen or restoring the process does not open it again.
 */
@Composable
fun ItsTime4ServiceNavGraph(launchCarId: String?, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    var isLaunchHandled by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val carIdArgument = listOf(navArgument(Destination.ARG_CAR_ID) { type = NavType.StringType })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in tabRoutes) {
                TabBar(currentRoute = currentRoute, onSelect = navController::navigateToTab)
            }
        },
        containerColor = ItsTime4ServiceTheme.colors.bg,
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
                    onOpenHistory = { carId ->
                        navController.navigate(Destination.History.createRoute(carId)) {
                            popUpTo(Destination.Cars.route)
                            launchSingleTop = true
                        }
                    },
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
                    onOpenHistory = {
                        navController.navigate(Destination.History.createRoute(carId)) {
                            popUpTo(Destination.Cars.route)
                            launchSingleTop = true
                        }
                    },
                    onAddTask = { navController.navigate(Destination.NewTask.createRoute(carId)) },
                    onOpenTask = { taskId -> navController.navigate(Destination.EditTask.createRoute(carId, taskId)) },
                )
            }
            composable(Destination.NewTask.route, arguments = carIdArgument) { entry ->
                TaskFormScreen(carId = entry.carId, taskId = null, onBack = { navController.popBackStack() })
            }
            composable(
                Destination.EditTask.route,
                arguments = carIdArgument + navArgument(Destination.ARG_TASK_ID) { type = NavType.StringType },
            ) { entry ->
                TaskFormScreen(carId = entry.carId, taskId = entry.taskId, onBack = { navController.popBackStack() })
            }
            composable(
                Destination.History.route,
                arguments = listOf(
                    navArgument(Destination.ARG_CAR_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                HistoryScreen(initialCarId = entry.arguments?.read { getStringOrNull(Destination.ARG_CAR_ID) })
            }
            composable(Destination.Settings.route) {
                SettingsScreen(onOpenLicenses = { navController.navigate(Destination.Licenses.route) })
            }
            composable(Destination.Licenses.route) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }
        }
        LaunchedEffect(Unit) {
            if (isLaunchHandled) return@LaunchedEffect
            isLaunchHandled = true
            if (launchCarId != null) navController.navigate(Destination.CarDetail.createRoute(launchCarId))
        }
    }
}

@Composable
private fun TabBar(currentRoute: String?, onSelect: (route: String) -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
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
                icon = ItsTime4ServiceIcons.Clock,
                label = stringResource(Res.string.tab_history),
                selected = currentRoute == Destination.History.route,
                onClick = { onSelect(Destination.History.createRoute()) },
                modifier = Modifier.weight(1f),
            )
            TabBarItem(
                icon = ItsTime4ServiceIcons.Car,
                label = stringResource(Res.string.tab_cars),
                selected = currentRoute == Destination.Cars.route,
                onClick = { onSelect(Destination.Cars.route) },
                modifier = Modifier.weight(1f),
            )
            TabBarItem(
                icon = ItsTime4ServiceIcons.Gear,
                label = stringResource(Res.string.tab_settings),
                selected = currentRoute == Destination.Settings.route,
                onClick = { onSelect(Destination.Settings.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val tabRoutes = setOf(Destination.History.route, Destination.Cars.route, Destination.Settings.route)

private fun NavHostController.navigateToTab(route: String) {
    if (route == Destination.Cars.route) {
        navigate(route) {
            popUpTo(Destination.Cars.route)
            launchSingleTop = true
        }
        return
    }
    navigate(route) {
        popUpTo(Destination.Cars.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val NavBackStackEntry.carId: String
    get() = arguments?.read { getString(Destination.ARG_CAR_ID) }.orEmpty()

private val NavBackStackEntry.taskId: String
    get() = arguments?.read { getString(Destination.ARG_TASK_ID) }.orEmpty()
