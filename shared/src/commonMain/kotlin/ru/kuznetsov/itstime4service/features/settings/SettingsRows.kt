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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_delete_car_with_history
import itstime4service.shared.generated.resources.car_sold
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.ui.components.buttons.AppIconButton
import ru.kuznetsov.itstime4service.ui.format.distanceText
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun CarList(cars: List<SettingsCar>, row: @Composable (SettingsCar) -> Unit) {
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
internal fun CarTitle(car: SettingsCar, distanceUnit: DistanceUnit, modifier: Modifier = Modifier) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            car.brand,
            style = typography.sans.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            color = if (car.isSold) colors.inkFaint else colors.ink,
        )
        Text(
            if (car.isSold) stringResource(Res.string.car_sold) else distanceText(car.mileageKm, distanceUnit),
            style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
            color = colors.inkFaint,
        )
    }
}

@Composable
internal fun CarOption(car: SettingsCar, distanceUnit: DistanceUnit, selected: Boolean, onClick: () -> Unit) {
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
internal fun PurgeRow(car: SettingsCar, distanceUnit: DistanceUnit, onDelete: () -> Unit) {
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
internal fun SettingsGroup(title: String, content: @Composable () -> Unit) {
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
internal fun SettingRow(
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
