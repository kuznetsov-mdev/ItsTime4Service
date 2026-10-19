package ru.kuznetsov.itstime4service.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.kuznetsov.itstime4service.AppViewModel
import ru.kuznetsov.itstime4service.features.cardetail.CarDetailViewModel
import ru.kuznetsov.itstime4service.features.carform.CarFormViewModel
import ru.kuznetsov.itstime4service.features.cars.CarsViewModel
import ru.kuznetsov.itstime4service.features.history.HistoryViewModel
import ru.kuznetsov.itstime4service.features.settings.SettingsViewModel
import ru.kuznetsov.itstime4service.features.taskform.TaskFormViewModel

val featureModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::CarsViewModel)
    viewModelOf(::CarFormViewModel)
    viewModelOf(::CarDetailViewModel)
    viewModel { params -> HistoryViewModel(params.values.firstOrNull() as String?, get(), get(), get(), get(), get(), get()) }
    viewModelOf(::SettingsViewModel)
    viewModel { params ->
        TaskFormViewModel(params.get<String>(0), params.values.getOrNull(1) as String?, get(), get(), get(), get(), get(), get())
    }
}
