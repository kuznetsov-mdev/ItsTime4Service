package ru.kuznetsov.itstime4service.features.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import org.jetbrains.compose.resources.StringResource
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
import ru.kuznetsov.itstime4service.ui.components.indicators.LocalScreenSettled
import ru.kuznetsov.itstime4service.ui.components.indicators.ReduceMotion
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
        val still = ReduceMotion.isEnabled()
        NavHost(
            navController = navController,
            startDestination = Destination.Cars.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { tabEnter(still) ?: fadeIn(tween(DEFAULT_FADE_MS)) },
            exitTransition = { tabExit(still) ?: fadeOut(tween(DEFAULT_FADE_MS)) },
            popEnterTransition = { tabEnter(still) ?: fadeIn(tween(DEFAULT_FADE_MS)) },
            popExitTransition = { tabExit(still) ?: fadeOut(tween(DEFAULT_FADE_MS)) },
        ) {
            screen(Destination.Cars.route) {
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
            screen(Destination.AddCar.route) {
                CarFormScreen(onBack = { navController.popBackStack() })
            }
            screen(Destination.CarDetail.route, arguments = carIdArgument) { entry ->
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
            screen(Destination.NewTask.route, arguments = carIdArgument) { entry ->
                TaskFormScreen(carId = entry.carId, taskId = null, onBack = { navController.popBackStack() })
            }
            screen(
                Destination.EditTask.route,
                arguments = carIdArgument + navArgument(Destination.ARG_TASK_ID) { type = NavType.StringType },
            ) { entry ->
                TaskFormScreen(carId = entry.carId, taskId = entry.taskId, onBack = { navController.popBackStack() })
            }
            screen(
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
            screen(Destination.Settings.route) {
                SettingsScreen(onOpenLicenses = { navController.navigate(Destination.Licenses.route) })
            }
            screen(Destination.Licenses.route) {
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
    val frames = remember { mutableStateMapOf<String, Rect>() }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
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
        Box(Modifier.onGloballyPositioned { barOrigin = it.positionInRoot() }) {
            Row {
                tabs.forEach { tab ->
                    TabBarItem(
                        icon = tab.icon,
                        label = stringResource(tab.label),
                        selected = currentRoute == tab.route,
                        onClick = { onSelect(tab.target) },
                        modifier = Modifier.weight(1f),
                        showFrame = false,
                        onFramePositioned = { frames[tab.route] = it.boundsInRoot() },
                    )
                }
            }
            frames[currentRoute]?.let { TabFrame(it.translate(-barOrigin)) }
        }
    }
}

@Composable
private fun TabFrame(bounds: Rect) {
    val density = LocalDensity.current
    val still = ReduceMotion.isEnabled()
    val spec = if (still) snap<Dp>() else tween<Dp>(TAB_SLIDE_MS, easing = FastOutSlowInEasing)
    val x by animateDpAsState(with(density) { bounds.left.toDp() }, spec)
    val width by animateDpAsState(with(density) { bounds.width.toDp() }, spec)
    Box(
        Modifier
            .offset(x = x, y = with(density) { bounds.top.toDp() })
            .size(width = width, height = with(density) { bounds.height.toDp() })
            .border(1.5.dp, ItsTime4ServiceTheme.colors.accent, RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)),
    )
}

/**
 * A tab of the bottom bar: the route it is selected on, the route it opens, its icon and label; [tabs] lists them
 * left to right, and that order decides which way the screens slide. The selected tab has one frame (`TabFrame`) that
 * slides to the newly selected entry and takes its width in the same time as the screens slide.
 */
private class Tab(val route: String, val target: String, val icon: ImageVector, val label: StringResource)

private val tabs = listOf(
    Tab(Destination.History.route, Destination.History.createRoute(), ItsTime4ServiceIcons.Clock, Res.string.tab_history),
    Tab(Destination.Cars.route, Destination.Cars.route, ItsTime4ServiceIcons.Car, Res.string.tab_cars),
    Tab(Destination.Settings.route, Destination.Settings.route, ItsTime4ServiceIcons.Gear, Res.string.tab_settings),
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): AnimatedContentTransitionScope.SlideDirection? {
    val from = tabs.indexOfFirst { it.route == initialState.destination.route }
    val to = tabs.indexOfFirst { it.route == targetState.destination.route }
    if (from < 0 || to < 0 || from == to) return null
    return if (to > from) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(still: Boolean): EnterTransition? =
    tabDirection()?.let { if (still) EnterTransition.None else slideIntoContainer(it, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)) }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(still: Boolean): ExitTransition? =
    tabDirection()?.let { if (still) ExitTransition.None else slideOutOfContainer(it, tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)) }

private const val TAB_SLIDE_MS = 500
private const val DEFAULT_FADE_MS = 700

private val tabRoutes = tabs.map { it.route }.toSet()

private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
    CompositionLocalProvider(LocalScreenSettled provides settled) { content(entry) }
}

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
