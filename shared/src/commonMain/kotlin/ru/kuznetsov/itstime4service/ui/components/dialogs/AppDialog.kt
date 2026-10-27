package ru.kuznetsov.itstime4service.ui.components.dialogs

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ru.kuznetsov.itstime4service.ui.components.ComponentPreview
import ru.kuznetsov.itstime4service.ui.icons.ItsTime4ServiceIcons

/**
 * The app's modal window: a [Dialog] that also takes the back gesture — the swipe from the left edge on iOS and the
 * system back on Android — so that it closes the window through [onDismiss], like "Cancel", instead of doing nothing
 * or leaving the screen underneath. A tap on the scrim closes it the same way.
 */
@Composable
fun AppDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = onDismiss,
        )
        content()
    }
}

@Composable
private fun AppDialogPreviewContent() {
    AppDialog(onDismiss = {}) {
        DialogCard(icon = ItsTime4ServiceIcons.Check, title = "Воздушный фильтр", modifier = Modifier.width(280.dp)) {}
    }
}

@Preview
@Composable
private fun AppDialogLightPreview() = ComponentPreview(darkTheme = false) {
    AppDialogPreviewContent()
}

@Preview
@Composable
private fun AppDialogDarkPreview() = ComponentPreview(darkTheme = true) {
    AppDialogPreviewContent()
}
