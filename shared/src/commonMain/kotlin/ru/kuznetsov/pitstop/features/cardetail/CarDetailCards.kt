package ru.kuznetsov.pitstop.features.cardetail

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
import pitstop.shared.generated.resources.Res
import pitstop.shared.generated.resources.action_done_unmark
import pitstop.shared.generated.resources.action_edit_mileage
import pitstop.shared.generated.resources.action_mark_done
import pitstop.shared.generated.resources.action_mileage_down
import pitstop.shared.generated.resources.action_mileage_up
import pitstop.shared.generated.resources.alert_count_due
import pitstop.shared.generated.resources.mileage_step_hint
import pitstop.shared.generated.resources.stat_mileage
import pitstop.shared.generated.resources.task_every
import pitstop.shared.generated.resources.task_left_suffix
import pitstop.shared.generated.resources.task_or_months
import pitstop.shared.generated.resources.task_overdue_suffix
import pitstop.shared.generated.resources.task_time_expired
import ru.kuznetsov.pitstop.domain.model.DistanceUnit
import ru.kuznetsov.pitstop.ui.components.buttons.SegmentedControl
import ru.kuznetsov.pitstop.ui.components.fields.InlineEditField
import ru.kuznetsov.pitstop.ui.components.indicators.ProgressBar
import ru.kuznetsov.pitstop.ui.components.indicators.StatusChip
import ru.kuznetsov.pitstop.ui.components.indicators.color
import ru.kuznetsov.pitstop.ui.format.distanceText
import ru.kuznetsov.pitstop.ui.format.grouped
import ru.kuznetsov.pitstop.ui.format.kmIn
import ru.kuznetsov.pitstop.ui.format.toTier
import ru.kuznetsov.pitstop.ui.format.unitGenitive
import ru.kuznetsov.pitstop.ui.icons.PitStopIcons
import ru.kuznetsov.pitstop.ui.theme.PitStopTheme

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
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusL)
    val chipShape = RoundedCornerShape(50)
    val unit = unitGenitive(distanceUnit)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.stat_mileage).uppercase(),
                style = typography.mono.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.06f.em),
                color = colors.inkFaint,
            )
            Text(
                stringResource(Res.string.alert_count_due, car.alertCount, car.taskCount),
                style = typography.mono.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                color = colors.onTier,
                modifier = Modifier
                    .clip(chipShape)
                    .background(car.worstStatus.toTier().color)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                icon = PitStopIcons.Minus,
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
                icon = PitStopIcons.Plus,
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
    val colors = PitStopTheme.colors
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusL)
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
    val colors = PitStopTheme.colors
    val typography = PitStopTheme.typography
    val tier = task.status.toTier()
    val shape = RoundedCornerShape(PitStopTheme.dimens.radiusL)
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
        Text(
            task.name,
            style = typography.sans.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            taskMeta(task, distanceUnit),
            style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
            color = colors.inkFaint,
            modifier = Modifier.padding(top = 2.dp),
        )
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
    val colors = PitStopTheme.colors
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
            PitStopIcons.Check,
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
