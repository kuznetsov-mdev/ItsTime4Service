package ru.kuznetsov.pitstop.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.kuznetsov.pitstop.AppViewModel
import ru.kuznetsov.pitstop.features.cardetail.CarDetailViewModel
import ru.kuznetsov.pitstop.features.carform.CarFormViewModel
import ru.kuznetsov.pitstop.features.cars.CarsViewModel
import ru.kuznetsov.pitstop.features.history.HistoryViewModel
import ru.kuznetsov.pitstop.features.settings.SettingsViewModel
import ru.kuznetsov.pitstop.features.taskform.TaskFormViewModel

val featureModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::CarsViewModel)
    viewModelOf(::CarFormViewModel)
    viewModelOf(::CarDetailViewModel)
    viewModel { params -> HistoryViewModel(params.values.firstOrNull() as String?, get(), get(), get(), get()) }
    viewModelOf(::SettingsViewModel)
    viewModel { params ->
        TaskFormViewModel(params.get<String>(0), params.values.getOrNull(1) as String?, get(), get(), get(), get(), get())
    }
}
