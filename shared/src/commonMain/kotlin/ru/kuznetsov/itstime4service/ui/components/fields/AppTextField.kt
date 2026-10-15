package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.car_name_placeholder
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Free-text input — brand, VIN, task name, date-as-text. Named `App…` to avoid colliding with `androidx.compose.material3.TextField`. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    minLines: Int = 1,
) {
    FieldBox(
        value = value,
        onValueChange = onValueChange,
        textStyle = ItsTime4ServiceTheme.typography.sans.copy(fontSize = 13.sp),
        shape = RoundedCornerShape(ItsTime4ServiceTheme.dimens.radiusM),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        isError = isError,
        minLines = minLines,
    )
}

@Preview
@Composable
private fun AppTextFieldLightPreview() = ComponentPreview(darkTheme = false) {
    AppTextField(value = "Kia Rio", onValueChange = {})
}

@Preview
@Composable
private fun AppTextFieldDarkPreview() = ComponentPreview(darkTheme = true) {
    AppTextField(value = "", onValueChange = {}, placeholder = stringResource(Res.string.car_name_placeholder))
}
