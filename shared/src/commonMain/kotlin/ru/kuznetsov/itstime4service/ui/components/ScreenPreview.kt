package ru.kuznetsov.itstime4service.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

@Composable
internal fun ScreenPreview(darkTheme: Boolean, content: @Composable () -> Unit) {
    ItsTime4ServiceTheme(darkTheme = darkTheme) {
        Box(Modifier.background(ItsTime4ServiceTheme.colors.bg)) {
            content()
        }
    }
}
