package ru.kuznetsov.itstime4service.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_cancel
import itstime4service.shared.generated.resources.action_delete
import itstime4service.shared.generated.resources.action_delete_car_with_history
import itstime4service.shared.generated.resources.app_name
import itstime4service.shared.generated.resources.dialog_purge_text
import itstime4service.shared.generated.resources.dialog_purge_title
import itstime4service.shared.generated.resources.notification_caption
import itstime4service.shared.generated.resources.notification_sample_full
import itstime4service.shared.generated.resources.notification_time_now
import itstime4service.shared.generated.resources.reminder_time_dialog_text
import itstime4service.shared.generated.resources.screen_settings
import itstime4service.shared.generated.resources.settings_delete_cars
import itstime4service.shared.generated.resources.settings_delete_note
import itstime4service.shared.generated.resources.settings_launch
import itstime4service.shared.generated.resources.settings_notifications
import itstime4service.shared.generated.resources.settings_open_default
import itstime4service.shared.generated.resources.settings_open_default_off
import itstime4service.shared.generated.resources.settings_open_default_on
import itstime4service.shared.generated.resources.settings_push_subtitle
import itstime4service.shared.generated.resources.settings_push_title
import itstime4service.shared.generated.resources.settings_remind_ahead
import itstime4service.shared.generated.resources.settings_remind_ahead_subtitle
import itstime4service.shared.generated.resources.settings_reminder_time
import itstime4service.shared.generated.resources.settings_reminder_time_subtitle
import itstime4service.shared.generated.resources.settings_theme
import itstime4service.shared.generated.resources.settings_units
import itstime4service.shared.generated.resources.theme_dark
import itstime4service.shared.generated.resources.theme_light
import itstime4service.shared.generated.resources.theme_system
import itstime4service.shared.generated.resources.units_km
import itstime4service.shared.generated.resources.units_mi
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.buttons.AppIconButton
import ru.kuznetsov.itstime4service.ui.components.buttons.SegmentedControl
import ru.kuznetsov.itstime4service.ui.components.cards.NotificationPreviewCard
import ru.kuznetsov.itstime4service.ui.components.dialogs.ConfirmDialog
import ru.kuznetsov.itstime4service.ui.components.dialogs.TimePickerDialog
import ru.kuznetsov.itstime4service.ui.components.indicators.AppSwitch
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.components.steppers.Stepper
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.toDisplayText
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

private val themeModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
private val distanceUnits = listOf(DistanceUnit.KILOMETERS, DistanceUnit.MILES)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onThemeSelect = viewModel::onThemeSelect,
        onUnitSelect = viewModel::onUnitSelect,
        onPushToggle = viewModel::onPushToggle,
        onReminderWindowChange = viewModel::onReminderWindowChange,
        onReminderTimeClick = viewModel::onReminderTimeClick,
        onReminderTimeDismiss = viewModel::onReminderTimeDismiss,
        onReminderTimeChange = viewModel::onReminderTimeChange,
        onOpenDefaultToggle = viewModel::onOpenDefaultToggle,
        onDefaultCarSelect = viewModel::onDefaultCarSelect,
        onPurgeRequest = viewModel::onPurgeRequest,
        onPurgeDismiss = viewModel::onPurgeDismiss,
        onPurgeConfirm = viewModel::onPurgeConfirm,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onThemeSelect: (ThemeMode) -> Unit,
    onUnitSelect: (DistanceUnit) -> Unit,
    onPushToggle: (Boolean) -> Unit,
    onReminderWindowChange: (steps: Int) -> Unit,
    onReminderTimeClick: () -> Unit,
    onReminderTimeDismiss: () -> Unit,
    onReminderTimeChange: (LocalTime) -> Unit,
    onOpenDefaultToggle: (Boolean) -> Unit,
    onDefaultCarSelect: (carId: String) -> Unit,
    onPurgeRequest: (carId: String) -> Unit,
    onPurgeDismiss: () -> Unit,
    onPurgeConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val purgeCar = state.purgeCar
    if (purgeCar != null) {
        ConfirmDialog(
            title = stringResource(Res.string.dialog_purge_title, purgeCar.brand),
            text = stringResource(Res.string.dialog_purge_text),
            confirmLabel = stringResource(Res.string.action_delete),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = onPurgeConfirm,
            onDismiss = onPurgeDismiss,
        )
    }
    val settings = state.settings
    if (settings != null && state.isPickingReminderTime) {
        TimePickerDialog(
            title = stringResource(Res.string.settings_reminder_time),
            text = stringResource(Res.string.reminder_time_dialog_text),
            time = settings.reminderTime,
            onConfirm = onReminderTimeChange,
            onDismiss = onReminderTimeDismiss,
        )
    }
    Column(modifier.fillMaxSize()) {
        ScreenTitle(title = stringResource(Res.string.screen_settings), modifier = Modifier.padding(top = 12.dp))
        if (settings == null) return@Column
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsGroup(stringResource(Res.string.settings_theme)) {
                SegmentedControl(
                    options = listOf(
                        stringResource(Res.string.theme_light),
                        stringResource(Res.string.theme_dark),
                        stringResource(Res.string.theme_system),
                    ),
                    selectedIndex = themeModes.indexOf(settings.themeMode),
                    onSelect = { onThemeSelect(themeModes[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SettingsGroup(stringResource(Res.string.settings_units)) {
                SegmentedControl(
                    options = listOf(stringResource(Res.string.units_km), stringResource(Res.string.units_mi)),
                    selectedIndex = distanceUnits.indexOf(settings.distanceUnit),
                    onSelect = { onUnitSelect(distanceUnits[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.cars.isNotEmpty()) {
                SettingsGroup(stringResource(Res.string.settings_launch)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingRow(
                            icon = ItsTime4ServiceIcons.Car,
                            title = stringResource(Res.string.settings_open_default),
                            subtitle = stringResource(
                                if (state.openDefault) Res.string.settings_open_default_on else Res.string.settings_open_default_off,
                            ),
                        ) {
                            AppSwitch(checked = state.openDefault, onCheckedChange = onOpenDefaultToggle)
                        }
                        if (state.openDefault) {
                            CarList(state.cars) { car ->
                                CarOption(
                                    car = car,
                                    distanceUnit = settings.distanceUnit,
                                    selected = car.id == state.defaultCarId,
                                    onClick = { onDefaultCarSelect(car.id) },
                                )
                            }
                        }
                    }
                }
            }
            SettingsGroup(stringResource(Res.string.settings_notifications)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        icon = ItsTime4ServiceIcons.Bell,
                        title = stringResource(Res.string.settings_push_title),
                        subtitle = stringResource(Res.string.settings_push_subtitle),
                    ) {
                        AppSwitch(checked = settings.pushEnabled, onCheckedChange = onPushToggle)
                    }
                    NotificationPreviewCard(
                        title = stringResource(Res.string.app_name),
                        time = stringResource(Res.string.notification_time_now),
                        text = stringResource(Res.string.notification_sample_full, distanceText(SAMPLE_REMAINING_KM, settings.distanceUnit)),
                        caption = stringResource(Res.string.notification_caption),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingRow(
                        icon = ItsTime4ServiceIcons.Gauge,
                        title = stringResource(Res.string.settings_remind_ahead),
                        subtitle = stringResource(Res.string.settings_remind_ahead_subtitle, unitGenitive(settings.distanceUnit)),
                    ) {
                        Stepper(
                            value = state.reminderSteps,
                            onValueChange = onReminderWindowChange,
                            min = SettingsUiState.MIN_REMINDER_STEPS,
                            max = SettingsUiState.MAX_REMINDER_STEPS,
                        )
                    }
                    SettingRow(
                        icon = ItsTime4ServiceIcons.Gear,
                        title = stringResource(Res.string.settings_reminder_time),
                        subtitle = stringResource(Res.string.settings_reminder_time_subtitle),
                        onClick = onReminderTimeClick,
                    ) {
                        Text(
                            settings.reminderTime.toDisplayText(),
                            style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                            color = ItsTime4ServiceTheme.colors.inkMuted,
                        )
                    }
                }
            }
            if (state.cars.isNotEmpty()) {
                SettingsGroup(stringResource(Res.string.settings_delete_cars)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(Res.string.settings_delete_note),
                            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                            color = ItsTime4ServiceTheme.colors.inkFaint,
                        )
                        CarList(state.cars) { car ->
                            PurgeRow(car = car, distanceUnit = settings.distanceUnit, onDelete = { onPurgeRequest(car.id) })
                        }
                    }
                }
            }
        }
    }
}

private const val SAMPLE_REMAINING_KM = 700

@Composable
private fun CarList(cars: List<SettingsCar>, row: @Composable (SettingsCar) -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape),
    ) {
        cars.forEachIndexed { index, car ->
            if (index > 0) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(colors.rule))
            row(car)
        }
    }
}

@Composable
private fun CarTitle(car: SettingsCar, distanceUnit: DistanceUnit, modifier: Modifier = Modifier) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            car.brand,
            style = typography.sans.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            color = if (car.isSold) colors.inkFaint else colors.ink,
        )
        Text(
            distanceText(car.mileageKm, distanceUnit),
            style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
            color = colors.inkFaint,
        )
    }
}

@Composable
private fun CarOption(car: SettingsCar, distanceUnit: DistanceUnit, selected: Boolean, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarTitle(car, distanceUnit, Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) colors.accent else colors.inkFaint, CircleShape)
                .padding(3.dp),
        ) {
            if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(colors.accent))
        }
    }
}

@Composable
private fun PurgeRow(car: SettingsCar, distanceUnit: DistanceUnit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarTitle(car, distanceUnit, Modifier.weight(1f))
        AppIconButton(
            icon = ItsTime4ServiceIcons.Trash,
            onClick = onDelete,
            contentDescription = stringResource(Res.string.action_delete_car_with_history, car.brand),
            iconSize = 15.dp,
            tint = ItsTime4ServiceTheme.colors.tierDue,
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = ItsTime4ServiceTheme.typography.sans.copy(
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04f.em,
            ),
            color = ItsTime4ServiceTheme.colors.inkMuted,
        )
        content()
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(31.dp).clip(CircleShape).background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = typography.sans.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            Text(
                subtitle,
                style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
                color = colors.inkFaint,
            )
        }
        trailing()
    }
}

private val previewState = SettingsUiState(
    settings = Settings(
        themeMode = ThemeMode.SYSTEM,
        distanceUnit = DistanceUnit.KILOMETERS,
        pushEnabled = true,
        defaultReminderWindowKm = 2_000,
        reminderTime = LocalTime(9, 0),
    ),
    cars = listOf(SettingsCar("c1", "Kia Rio", 84_300), SettingsCar("c2", "Toyota Camry", 152_000)),
    openDefault = true,
    defaultCarId = "c2",
)

@Composable
private fun SettingsPreviewContent() {
    SettingsContent(
        state = previewState,
        onThemeSelect = {},
        onUnitSelect = {},
        onPushToggle = {},
        onReminderWindowChange = {},
        onReminderTimeClick = {},
        onReminderTimeDismiss = {},
        onReminderTimeChange = {},
        onOpenDefaultToggle = {},
        onDefaultCarSelect = {},
        onPurgeRequest = {},
        onPurgeDismiss = {},
        onPurgeConfirm = {},
    )
}

@Preview
@Composable
private fun SettingsContentLightPreview() = ScreenPreview(darkTheme = false) {
    SettingsPreviewContent()
}

@Preview
@Composable
private fun SettingsContentDarkPreview() = ScreenPreview(darkTheme = true) {
    SettingsPreviewContent()
}
