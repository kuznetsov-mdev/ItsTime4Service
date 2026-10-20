package ru.kuznetsov.itstime4service.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_filter
import itstime4service.shared.generated.resources.action_select_car
import itstime4service.shared.generated.resources.history_all_cars
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun HistoryBar(
    state: HistoryUiState,
    onCarMenuToggle: () -> Unit,
    onCarMenuDismiss: () -> Unit,
    onCarSelect: (carId: String?) -> Unit,
    onFilterOpen: () -> Unit,
) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)
    val selectedCar = state.selectedCar
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).onSizeChanged { fieldWidthPx = it.width }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface2)
                    .border(1.dp, colors.rule, shape)
                    .clickable(onClickLabel = stringResource(Res.string.action_select_car), onClick = onCarMenuToggle)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedCar?.label() ?: stringResource(Res.string.history_all_cars),
                    style = typography.sans.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = if (selectedCar?.isSold == true) colors.inkFaint else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    ItsTime4ServiceIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.inkFaint,
                    modifier = Modifier.size(14.dp).rotate(if (state.isCarMenuOpen) 180f else 0f),
                )
            }
            DropdownMenu(
                expanded = state.isCarMenuOpen,
                onDismissRequest = onCarMenuDismiss,
                containerColor = colors.surface,
                modifier = Modifier.width(with(LocalDensity.current) { fieldWidthPx.toDp() }),
            ) {
                CarMenuItem(
                    label = stringResource(Res.string.history_all_cars),
                    selected = selectedCar == null,
                    sold = false,
                    onClick = { onCarSelect(null) },
                )
                state.cars.forEach { car ->
                    CarMenuItem(
                        label = car.label(),
                        selected = car.id == state.selectedCarId,
                        sold = car.isSold,
                        onClick = { onCarSelect(car.id) },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(shape)
                .background(colors.surface2)
                .border(1.dp, if (state.filter.isActive) colors.accent else colors.rule, shape)
                .clickable(onClick = onFilterOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ItsTime4ServiceIcons.Filter,
                contentDescription = stringResource(Res.string.action_filter),
                tint = if (state.filter.isActive) colors.accent else colors.inkMuted,
                modifier = Modifier.size(17.dp),
            )
            if (state.filter.isActive) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
    }
}

@Composable
private fun CarMenuItem(label: String, selected: Boolean, sold: Boolean, onClick: () -> Unit) {
    val colors = ItsTime4ServiceTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = ItsTime4ServiceTheme.typography.sans.copy(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = when {
                    sold -> colors.inkFaint
                    selected -> colors.accent
                    else -> colors.ink
                },
            )
        },
        onClick = onClick,
    )
}
