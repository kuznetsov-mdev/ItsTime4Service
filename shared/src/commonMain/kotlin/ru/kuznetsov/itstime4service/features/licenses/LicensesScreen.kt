package ru.kuznetsov.itstime4service.features.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_hide_license
import itstime4service.shared.generated.resources.action_show_license
import itstime4service.shared.generated.resources.licenses_subtitle
import itstime4service.shared.generated.resources.settings_licenses
import ru.kuznetsov.itstime4service.ui.components.ScreenPreview
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenHorizontalPadding
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTitle
import ru.kuznetsov.itstime4service.ui.components.screen.ScreenTopBar
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(onBack = onBack)
        ScreenTitle(
            title = stringResource(Res.string.settings_licenses),
            subtitle = stringResource(Res.string.licenses_subtitle),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(openSourceLibraries, key = { it.name }) { library ->
                LibraryCard(library)
            }
        }
    }
}

@Composable
private fun LibraryCard(library: OpenSourceLibrary) {
    val colors = ItsTime4ServiceTheme.colors
    val typography = ItsTime4ServiceTheme.typography
    val shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM)
    var isExpanded by rememberSaveable(library.name) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.rule, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(
                        if (isExpanded) Res.string.action_hide_license else Res.string.action_show_license,
                    ),
                    onClick = { isExpanded = !isExpanded },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    library.name,
                    style = typography.sans.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                Text(
                    library.license,
                    style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
                    color = colors.inkMuted,
                )
                Text(
                    library.url,
                    style = typography.mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
                    color = colors.inkFaint,
                )
            }
            Icon(
                ItsTime4ServiceIcons.ChevronDown,
                contentDescription = null,
                tint = colors.inkFaint,
                modifier = Modifier.size(16.dp).rotate(if (isExpanded) 180f else 0f),
            )
        }
        if (isExpanded) LicenseText(library.licenseFile)
    }
}

@Composable
private fun LicenseText(file: String) {
    val colors = ItsTime4ServiceTheme.colors
    val text by produceState("", file) { value = Res.readBytes(file).decodeToString().trim() }
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        style = ItsTime4ServiceTheme.typography.mono.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
        color = colors.inkMuted,
    )
}

@Preview
@Composable
private fun LicensesScreenLightPreview() = ScreenPreview(darkTheme = false) {
    LicensesScreen(onBack = {})
}

@Preview
@Composable
private fun LicensesScreenDarkPreview() = ScreenPreview(darkTheme = true) {
    LicensesScreen(onBack = {})
}
