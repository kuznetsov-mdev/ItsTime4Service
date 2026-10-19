package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.comment_placeholder
import itstime4service.shared.generated.resources.field_comment
import itstime4service.shared.generated.resources.field_cost_rub
import itstime4service.shared.generated.resources.field_service_name
import itstime4service.shared.generated.resources.service_name_placeholder
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview

/**
 * The optional fields of a service history record — where it was serviced, the cost and a comment — shared by
 * the done-mark window, the history record windows and the task forms. The keyboard's done key calls [onDone].
 */
@Composable
fun ServiceRecordFields(
    serviceName: String,
    cost: String,
    comment: String,
    onServiceNameChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledMiniField(
            label = stringResource(Res.string.field_service_name),
            value = serviceName,
            onValueChange = onServiceNameChange,
            onDone = onDone,
            placeholder = stringResource(Res.string.service_name_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
        LabeledMiniField(
            label = stringResource(Res.string.field_cost_rub),
            value = cost,
            onValueChange = onCostChange,
            onDone = onDone,
            placeholder = "2400",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        LabeledMiniField(
            label = stringResource(Res.string.field_comment),
            value = comment,
            onValueChange = onCommentChange,
            onDone = onDone,
            placeholder = stringResource(Res.string.comment_placeholder),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServiceRecordFieldsPreviewContent() {
    ServiceRecordFields(
        serviceName = "Kia Center",
        cost = "4200",
        comment = "",
        onServiceNameChange = {},
        onCostChange = {},
        onCommentChange = {},
        onDone = {},
    )
}

@Preview
@Composable
private fun ServiceRecordFieldsLightPreview() = ComponentPreview(darkTheme = false) {
    ServiceRecordFieldsPreviewContent()
}

@Preview
@Composable
private fun ServiceRecordFieldsDarkPreview() = ComponentPreview(darkTheme = true) {
    ServiceRecordFieldsPreviewContent()
}
