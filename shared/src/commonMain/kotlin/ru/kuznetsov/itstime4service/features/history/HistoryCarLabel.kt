package ru.kuznetsov.itstime4service.features.history

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.history_sold_car

@Composable
internal fun HistoryCar.label(): String = if (isSold) stringResource(Res.string.history_sold_car, brand) else brand
