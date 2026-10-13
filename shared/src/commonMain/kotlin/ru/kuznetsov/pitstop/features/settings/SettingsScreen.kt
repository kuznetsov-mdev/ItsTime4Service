package ru.kuznetsov.pitstop.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.reminder_time_dialog_text
import pitstop.shared.generated.resources.app_name
import pitstop.shared.generated.resources.notification_caption
import pitstop.shared.generated.resources.notification_sample_full
import pitstop.shared.generated.resources.notification_time_now
import pitstop.shared.generated.resources.screen_settings
import pitstop.shared.generated.resources.settings_notifications
import pitstop.shared.generated.resources.settings_push_subtitle
import pitstop.shared.generated.resources.settings_push_title
import pitstop.shared.generated.resources.settings_remind_ahead
import pitstop.shared.generated.resources.settings_remind_ahead_subtitle
import pitstop.shared.generated.resources.settings_reminder_time
import pitstop.shared.generated.resources.settings_reminder_time_subtitle
import pitstop.shared.generated.resources.settings_theme
import pitstop.shared.generated.resources.settings_units
import pitstop.shared.generated.resources.theme_dark
import pitstop.shared.generated.resources.theme_light
import pitstop.shared.generated.resources.theme_system
import pitstop.shared.generated.resources.units_km
import pitstop.shared.generated.resources.units_mi
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.domain.model.Settings
import ru.kuznetsov.pitstop.domain.model.ThemeMode
import ru.kuznetsov.pitstop.ui.components.dialogs.TimePickerDialog
import ru.kuznetsov.pitstop.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.pitstop.ui.components.ScreenPreview
import ru.kuznetsov.pitstop.ui.components.screen.ScreenTitle
import ru.kuznetsov.pitstop.ui.format.toDisplayText
import ru.kuznetsov.pitstop.ui.components.buttons.SegmentedControl
import ru.kuznetsov.pitstop.ui.components.cards.NotificationPreviewCard
import ru.kuznetsov.pitstop.ui.components.indicators.AppSwitch
import ru.kuznetsov.pitstop.ui.components.steppers.Stepper
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

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
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onThemeSelect: (ThemeMode) -> Unit,
    onUnitSelect: (DistanceUnit) -> Unit,
    onPushToggle: (Boolean) -> Unit,
    onReminderWindowChange: (thousandKm: Int) -> Unit,
    onReminderTimeClick: () -> Unit,
    onReminderTimeDismiss: () -> Unit,
    onReminderTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            SettingsGroup(stringResource(Res.string.settings_notifications)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        icon = PitStopIcons.Bell,
                        title = stringResource(Res.string.settings_push_title),
                        subtitle = stringResource(Res.string.settings_push_subtitle),
                    ) {
                        AppSwitch(checked = settings.pushEnabled, onCheckedChange = onPushToggle)
                    }
                    NotificationPreviewCard(
                        title = stringResource(Res.string.app_name),
                        time = stringResource(Res.string.notification_time_now),
                        text = stringResource(Res.string.notification_sample_full),
                        caption = stringResource(Res.string.notification_caption),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingRow(
                        icon = PitStopIcons.Gauge,
                        title = stringResource(Res.string.settings_remind_ahead),
                        subtitle = stringResource(Res.string.settings_remind_ahead_subtitle),
                    ) {
                        Stepper(
                            value = settings.defaultReminderWindowKm / SettingsViewModel.KM_PER_STEP,
                            onValueChange = onReminderWindowChange,
                            min = 1,
                            max = 9,
                        )
                    }
                    SettingRow(
                        icon = PitStopIcons.Gear,
                        title = stringResource(Res.string.settings_reminder_time),
                        subtitle = stringResource(Res.string.settings_reminder_time_subtitle),
                        onClick = onReminderTimeClick,
                    ) {
                        Text(
                            settings.reminderTime.toDisplayText(),
                            style = PitStopTheme.typography.mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                            color = PitStopTheme.colors.inkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = PitStopTheme.typography.sans.copy(
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.04f.em,
            ),
            color = PitStopTheme.colors.inkMuted,
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
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusM)
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
