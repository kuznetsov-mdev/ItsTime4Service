package ru.kuznetsov.itstime4service.ui.format

import java.util.Locale

actual fun deviceRegion(): String? = Locale.getDefault().country.takeIf { it.isNotEmpty() }
