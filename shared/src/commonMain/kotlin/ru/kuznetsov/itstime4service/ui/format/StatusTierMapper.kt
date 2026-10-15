package ru.kuznetsov.itstime4service.ui.format

import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.ui.components.indicators.StatusTier

fun TaskStatus.toTier(): StatusTier = when (this) {
    TaskStatus.OK -> StatusTier.Ok
    TaskStatus.SOON -> StatusTier.Soon
    TaskStatus.DUE -> StatusTier.Due
}
