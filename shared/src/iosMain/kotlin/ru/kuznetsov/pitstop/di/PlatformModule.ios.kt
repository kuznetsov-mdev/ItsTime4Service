package ru.kuznetsov.pitstop.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ru.kuznetsov.pitstop.data.local.DatabaseDriverFactory

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
}
