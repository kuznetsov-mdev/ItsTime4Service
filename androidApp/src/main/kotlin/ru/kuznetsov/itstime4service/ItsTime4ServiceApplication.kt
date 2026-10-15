package ru.kuznetsov.itstime4service

import android.app.Application
import org.koin.android.ext.koin.androidContext
import ru.kuznetsov.itstime4service.di.initKoin

class ItsTime4ServiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@ItsTime4ServiceApplication) }
    }
}
