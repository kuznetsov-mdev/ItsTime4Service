package ru.kuznetsov.itstime4service.features.cardetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_done_unmark
import itstime4service.shared.generated.resources.action_edit_mileage
import itstime4service.shared.generated.resources.action_mark_done
import itstime4service.shared.generated.resources.action_mileage_down
import itstime4service.shared.generated.resources.action_mileage_up
import itstime4service.shared.generated.resources.alert_count_due
import itstime4service.shared.generated.resources.mileage_step_hint
import itstime4service.shared.generated.resources.stat_mileage
import itstime4service.shared.generated.resources.task_every
import itstime4service.shared.generated.resources.task_left_suffix
import itstime4service.shared.generated.resources.task_or_months
import itstime4service.shared.generated.resources.task_overdue_suffix
import itstime4service.shared.generated.resources.task_time_expired
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.buttons.SegmentedControl
import ru.kuznetsov.itstime4service.ui.components.fields.InlineEditField
import ru.kuznetsov.itstime4service.ui.components.indicators.ProgressBar
import ru.kuznetsov.itstime4service.ui.components.indicators.StatusChip
import ru.kuznetsov.itstime4service.ui.components.indicators.StatusDonut
import ru.kuznetsov.itstime4service.ui.components.indicators.color
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.kmIn
import ru.kuznetsov.itstime4service.ui.format.toTier
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.icons.vector
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

private const val MAX_MILEAGE_LENGTH = 7

internal fun Modifier.dashedUnderline(color: Color): Modifier = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
    )
}

@Composable
internal fun MileageCard(
    car: CarDetails,
    distanceUnit: DistanceUnit,
    multiplier: Int,
    isEditing: Boolean,
    editBounds: Modifier,
    onEdit: () -> Unit,
    onSave: (String) -> Unit,
    onStep: (direction: Int) -> Unit,
    onMultiplierSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    val unit = unitGenitive(distanceUnit)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.stat_mileage).uppercase(),
                    style = typography.mono.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.06f.em),
                    color = colors.inkFaint,
                )
                if (isEditing) {
                    InlineEditField(
                        initialValue = car.mileageKm.kmIn(distanceUnit).toString(),
                        onSave = onSave,
                        transform = { it.filter(Char::isDigit).take(MAX_MILEAGE_LENGTH) },
                        canSave = { (it.toIntOrNull() ?: 0) > 0 },
                        modifier = editBounds.padding(top = 8.dp),
                    )
                } else {
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            car.mileageKm.kmIn(distanceUnit).grouped(),
                            style = typography.display.copy(fontSize = 26.sp, lineHeight = 32.sp),
                            color = colors.ink,
                            modifier = Modifier
                                .clickable(onClickLabel = stringResource(Res.string.action_edit_mileage), onClick = onEdit)
                                .dashedUnderline(colors.inkFaint),
                        )
                        Text(
                            unit,
                            style = typography.sans.copy(fontSize = 13.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.inkFaint,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                }
            }
            StatusDonut(
                due = car.dueCount,
                soon = car.soonCount,
                total = car.taskCount,
                contentDescription = stringResource(Res.string.alert_count_due, car.dueCount + car.soonCount, car.taskCount),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                icon = ItsTime4ServiceIcons.Minus,
                contentDescription = stringResource(Res.string.action_mileage_down),
                accent = false,
                enabled = !isEditing,
                onClick = { onStep(-1) },
            )
            SegmentedControl(
                options = CarDetailUiState.MILEAGE_MULTIPLIERS.map { "×$it" },
                selectedIndex = CarDetailUiState.MILEAGE_MULTIPLIERS.indexOf(multiplier),
                onSelect = { onMultiplierSelect(CarDetailUiState.MILEAGE_MULTIPLIERS[it]) },
                modifier = Modifier.weight(1f),
            )
            StepButton(
                icon = ItsTime4ServiceIcons.Plus,
                contentDescription = stringResource(Res.string.action_mileage_up),
                accent = true,
                enabled = !isEditing,
                onClick = { onStep(1) },
            )
        }
        Text(
            stringResource(Res.string.mileage_step_hint, "${(multiplier * CarDetailUiState.MILEAGE_STEP).grouped()} $unit"),
            style = typography.sans.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = colors.inkFaint,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StepButton(icon: ImageVector, contentDescription: String, accent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (accent) colors.accent else colors.surface2)
            .border(1.dp, if (accent) colors.accent else colors.rule, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (accent) colors.onAccent else colors.ink, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun TaskCard(
    task: TaskItem,
    distanceUnit: DistanceUnit,
    onOpen: () -> Unit,
    onMarkDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val tier = task.status.toTier()
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    val remaining = kotlin.math.abs(task.remainingKm).kmIn(distanceUnit).grouped()
    val unit = unitGenitive(distanceUnit)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    remaining,
                    style = typography.display.copy(fontSize = 20.sp, lineHeight = 24.sp),
                    color = tier.color,
                )
                Text(
                    stringResource(if (task.remainingKm <= 0) Res.string.task_overdue_suffix else Res.string.task_left_suffix, unit),
                    style = typography.sans.copy(fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = tier.color,
                    modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                )
            }
            StatusChip(status = tier)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(task.icon.vector, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(15.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    task.name,
                    style = typography.sans.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    taskMeta(task, distanceUnit),
                    style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressBar(value = task.progress, tier = tier, modifier = Modifier.weight(1f))
            DoneCircle(isDone = task.isDone, onClick = onMarkDone)
        }
    }
}

@Composable
private fun DoneCircle(isDone: Boolean, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (isDone) colors.tierOk else colors.surface)
            .border(1.5.dp, if (isDone) colors.tierOk else colors.rule, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            ItsTime4ServiceIcons.Check,
            contentDescription = stringResource(if (isDone) Res.string.action_done_unmark else Res.string.action_mark_done),
            tint = if (isDone) colors.onTier else colors.inkFaint,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun taskMeta(task: TaskItem, distanceUnit: DistanceUnit): String {
    val parts = mutableListOf(stringResource(Res.string.task_every, distanceText(task.intervalKm, distanceUnit)))
    val remainingMonths = task.remainingMonths
    if (remainingMonths != null) {
        parts += if (remainingMonths <= 0) {
            stringResource(Res.string.task_time_expired)
        } else {
            stringResource(Res.string.task_or_months, remainingMonths)
        }
    }
    return parts.joinToString(" · ")
}
