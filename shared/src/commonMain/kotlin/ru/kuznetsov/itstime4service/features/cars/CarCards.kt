package ru.kuznetsov.itstime4service.features.cars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_add_car
import itstime4service.shared.generated.resources.action_hide_plate
import itstime4service.shared.generated.resources.action_show_plate
import itstime4service.shared.generated.resources.alert_count_due
import itstime4service.shared.generated.resources.car_card_no_history
import itstime4service.shared.generated.resources.car_card_no_tasks
import itstime4service.shared.generated.resources.car_card_recent
import itstime4service.shared.generated.resources.car_card_upcoming
import itstime4service.shared.generated.resources.cars_view_list
import itstime4service.shared.generated.resources.cars_view_tasks
import itstime4service.shared.generated.resources.cars_view_tasks_history
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.indicators.FlippablePlate
import ru.kuznetsov.itstime4service.ui.components.indicators.StatusDonut
import ru.kuznetsov.itstime4service.ui.components.indicators.color
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.kmIn
import ru.kuznetsov.itstime4service.ui.format.toTier
import ru.kuznetsov.itstime4service.ui.format.unitGenitive
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun CarsViewToggle(selected: CarsView, onSelect: (CarsView) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ViewToggleButton(ItsTime4ServiceIcons.List, stringResource(Res.string.cars_view_list), selected == CarsView.LIST) {
            onSelect(CarsView.LIST)
        }
        ViewToggleButton(ItsTime4ServiceIcons.PanelTop, stringResource(Res.string.cars_view_tasks), selected == CarsView.TASKS) {
            onSelect(CarsView.TASKS)
        }
        ViewToggleButton(
            ItsTime4ServiceIcons.LayoutPanelTop,
            stringResource(Res.string.cars_view_tasks_history),
            selected == CarsView.TASKS_AND_HISTORY,
        ) { onSelect(CarsView.TASKS_AND_HISTORY) }
    }
}

@Composable
private fun ViewToggleButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    Box(
        modifier = Modifier
            .size(31.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.surface2)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) colors.accent else colors.inkMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun AddCarRow(onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 66.dp) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusXl)
    val edge = colors.accent.copy(alpha = 0.5f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(colors.accent.copy(alpha = 0.06f))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = size.copy(width = size.width - stroke, height = size.height - stroke),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            ItsTime4ServiceIcons.Plus,
            contentDescription = stringResource(Res.string.action_add_car),
            tint = colors.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * A car card of the expanded list: brand, mileage and the status ring on top; the upcoming tasks and, with
 * [withHistory], the latest history records below; the licence plate at the bottom, framed by a gentle arc
 * that the column divider runs into. Tapping the top or the tasks opens the car, the history opens it in History.
 */
@Composable
internal fun CarCard(
    car: CarItem,
    withHistory: Boolean,
    distanceUnit: DistanceUnit,
    onOpenCar: () -> Unit,
    onOpenHistory: () -> Unit,
    onPlateToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusXl)
    val hasPlate = car.plateLook != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCar).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    car.brand,
                    style = typography.sans.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        car.mileageKm.kmIn(distanceUnit).grouped(),
                        style = typography.display.copy(fontSize = 20.sp, lineHeight = 26.sp),
                        color = colors.ink,
                    )
                    Text(
                        unitGenitive(distanceUnit),
                        style = typography.sans.copy(fontSize = 11.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.inkFaint,
                        modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                    )
                }
            }
            StatusDonut(
                due = car.dueCount,
                soon = car.soonCount,
                total = car.taskCount,
                contentDescription = stringResource(Res.string.alert_count_due, car.dueCount + car.soonCount, car.taskCount),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            CardColumn(
                title = stringResource(Res.string.car_card_upcoming),
                emptyText = stringResource(Res.string.car_card_no_tasks).takeIf { car.upcomingTasks.isEmpty() },
                hasPlate = hasPlate,
                onClick = onOpenCar,
                modifier = Modifier.weight(1f),
            ) {
                car.upcomingTasks.forEach { task ->
                    CardLine(
                        text = task.name,
                        value = (if (task.remainingKm <= 0) "−" else "") + abs(task.remainingKm).kmIn(distanceUnit).grouped(),
                        valueColor = task.status.toTier().color,
                    )
                }
            }
            if (withHistory) {
                Box(Modifier.fillMaxHeight().width(1.dp).background(colors.rule))
                CardColumn(
                    title = stringResource(Res.string.car_card_recent),
                    emptyText = stringResource(Res.string.car_card_no_history).takeIf { car.recentRecords.isEmpty() },
                    hasPlate = hasPlate,
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f),
                ) {
                    car.recentRecords.forEach { record ->
                        val date = record.date
                        CardLine(
                            text = record.taskName,
                            value = "${date.day.twoDigits()}.${date.month.number.twoDigits()}.${(date.year % 100).twoDigits()}",
                            valueColor = colors.inkFaint,
                        )
                    }
                }
            }
        }
        if (car.plateLook != null) {
            PlateSection(withDivider = withHistory) {
                FlippablePlate(
                    look = car.plateLook,
                    hidden = car.plateHidden,
                    compact = true,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(if (car.plateHidden) Res.string.action_show_plate else Res.string.action_hide_plate),
                        onClick = onPlateToggle,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CardColumn(
    title: String,
    emptyText: String?,
    hasPlate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lines: @Composable () -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = if (hasPlate) 2.dp else 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title.uppercase(),
            style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.06f.em),
            color = colors.inkFaint,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        if (emptyText != null) {
            Text(emptyText, style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.inkFaint)
        } else {
            lines()
        }
    }
}

@Composable
private fun CardLine(text: String, value: String, valueColor: Color) {
    val colors = ItsTime4ServiceTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlateSection(withDivider: Boolean, plate: @Composable () -> Unit) {
    val rule = ItsTime4ServiceTheme.colors.rule
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                val top = 5.dp.toPx()
                val scaleY = (size.height - 4.dp.toPx()) / ARC_HEIGHT
                fun x(v: Float) = size.width * v / 100f
                fun y(v: Float) = top + v * scaleY
                val arc = Path().apply {
                    moveTo(x(5f), y(40f))
                    cubicTo(x(15f), y(40f), x(17f), y(3f), x(28f), y(2f))
                    lineTo(x(72f), y(2f))
                    cubicTo(x(83f), y(3f), x(85f), y(40f), x(95f), y(40f))
                }
                drawPath(arc, color = rule, style = Stroke(width = stroke))
                if (withDivider) drawLine(rule, Offset(size.width / 2, 0f), Offset(size.width / 2, y(2f)), strokeWidth = stroke)
            }
            .padding(top = 17.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        plate()
    }
}

private const val ARC_HEIGHT = 40f

private fun Int.twoDigits(): String = toString().padStart(2, '0')
