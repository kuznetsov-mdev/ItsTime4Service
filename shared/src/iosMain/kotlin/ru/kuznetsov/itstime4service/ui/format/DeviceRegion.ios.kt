package ru.kuznetsov.itstime4service.ui.format

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

actual fun deviceRegion(): String? = NSLocale.currentLocale.countryCode
