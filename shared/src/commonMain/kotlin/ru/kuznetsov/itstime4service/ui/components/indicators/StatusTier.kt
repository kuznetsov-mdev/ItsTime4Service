package ru.kuznetsov.itstime4service.ui.components.indicators

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.status_due
import itstime4service.shared.generated.resources.status_ok
import itstime4service.shared.generated.resources.status_soon
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme

/** Maintenance status tier as the ui-kit sees it — drives the color and label of every status indicator. */
enum class StatusTier { Ok, Soon, Due }

val StatusTier.color: Color
    @Composable get() = when (this) {
        StatusTier.Ok -> ItsTime4ServiceTheme.colors.tierOk
        StatusTier.Soon -> ItsTime4ServiceTheme.colors.tierSoon
        StatusTier.Due -> ItsTime4ServiceTheme.colors.tierDue
    }

val StatusTier.label: String
    @Composable get() = when (this) {
        StatusTier.Ok -> stringResource(Res.string.status_ok)
        StatusTier.Soon -> stringResource(Res.string.status_soon)
        StatusTier.Due -> stringResource(Res.string.status_due)
    }
