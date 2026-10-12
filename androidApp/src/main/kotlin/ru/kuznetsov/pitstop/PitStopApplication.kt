package ru.kuznetsov.pitstop

import android.app.Application
import org.koin.android.ext.koin.androidContext
import ru.kuznetsov.pitstop.di.initKoin

class PitStopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@PitStopApplication) }
    }
}
