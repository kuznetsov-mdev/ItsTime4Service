package ru.kuznetsov.itstime4service.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.cost_rub
import itstime4service.shared.generated.resources.months
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.format.grouped
import ru.kuznetsov.itstime4service.ui.format.toDisplayText
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun YearHeader(year: Int) {
    val colors = ItsTime4ServiceTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.bg).padding(top = 10.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
        Text(
            year.toString(),
            style = ItsTime4ServiceTheme.typography.display.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = colors.inkMuted,
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.rule))
    }
}

@Composable
internal fun MonthCard(
    month: HistoryMonth,
    showCar: Boolean,
    distanceUnit: DistanceUnit,
    onEntryOpen: (HistoryRow) -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusL)
    val chipShape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, bottom = 12.dp)) {
        Column(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.rule, shape)
                .padding(top = 8.dp),
        ) {
            month.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(colors.rule))
                }
                HistoryRowItem(row = row, showCar = showCar, distanceUnit = distanceUnit, onClick = { onEntryOpen(row) })
            }
        }
        Text(
            stringArrayResource(Res.array.months)[month.month - 1].uppercase(),
            style = ItsTime4ServiceTheme.typography.mono.copy(
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.06f.em,
            ),
            color = colors.inkFaint,
            modifier = Modifier
                .offset(x = 14.dp)
                .clip(chipShape)
                .background(colors.surface)
                .border(1.dp, colors.rule, chipShape)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun HistoryRowItem(row: HistoryRow, showCar: Boolean, distanceUnit: DistanceUnit, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val entry = row.entry
    val metaStyle = typography.mono.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal)
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                entry.taskName,
                style = typography.sans.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(entry.date.toDisplayText(), style = metaStyle, color = colors.inkFaint, modifier = Modifier.alignByBaseline())
        }
        val meta = listOfNotNull(
            distanceText(entry.mileageKm, distanceUnit),
            entry.costRub?.let { stringResource(Res.string.cost_rub, it.grouped()) },
            entry.serviceName,
        ).joinToString("   ")
        if (showCar) {
            Text(
                row.car.label(),
                style = typography.sans.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                color = if (row.car.isSold) colors.inkFaint else colors.ink,
            )
        }
        Text(meta, style = metaStyle, color = colors.inkMuted)
        if (entry.comment != null) {
            Text(
                entry.comment,
                style = typography.sans.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkMuted,
            )
        }
    }
}

@Composable
internal fun EmptyHistory(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = ItsTime4ServiceTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}
