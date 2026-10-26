package ru.kuznetsov.itstime4service.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.components.fields.ServiceRecordFields
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons

/**
 * The fields of a modal window, placed under its title: they take the height the window has left and scroll when
 * they do not fit, so the icon, the title, the error bar and the buttons below stay on screen.
 */
@Composable
fun ColumnScope.DialogFields(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .padding(top = 14.dp)
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun DialogFieldsPreviewContent() {
    DialogCard(icon = ItsTime4ServiceIcons.Check, title = "Воздушный фильтр", modifier = Modifier.width(280.dp)) {
        DialogFields {
            ServiceRecordFields(
                serviceName = "АвтоСервис Юг",
                cost = "900",
                comment = "",
                onServiceNameChange = {},
                onCostChange = {},
                onCommentChange = {},
                onDone = {},
            )
        }
    }
}

@Preview
@Composable
private fun DialogFieldsLightPreview() = ComponentPreview(darkTheme = false) {
    DialogFieldsPreviewContent()
}

@Preview
@Composable
private fun DialogFieldsDarkPreview() = ComponentPreview(darkTheme = true) {
    DialogFieldsPreviewContent()
}
