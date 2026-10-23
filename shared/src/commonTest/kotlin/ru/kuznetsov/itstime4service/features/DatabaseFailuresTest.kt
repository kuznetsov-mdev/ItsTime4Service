package ru.kuznetsov.itstime4service.features

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.AppViewModel
import ru.kuznetsov.itstime4service.Launch
import ru.kuznetsov.itstime4service.domain.model.CarsView
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.ThemeMode
import ru.kuznetsov.itstime4service.features.cardetail.CarDetailViewModel
import ru.kuznetsov.itstime4service.features.cardetail.EditableField
import ru.kuznetsov.itstime4service.features.carform.CarFormViewModel
import ru.kuznetsov.itstime4service.features.cars.CarsViewModel
import ru.kuznetsov.itstime4service.features.history.HistoryViewModel
import ru.kuznetsov.itstime4service.features.settings.SettingsViewModel
import ru.kuznetsov.itstime4service.features.taskform.TaskFormViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers what the view models do when the local database fails, on the app's own graph: a trigger makes every write
 * to a table fail, a renamed table makes every read of it fail. A failed write raises the "could not save" flag, keeps
 * the form or the window with what was typed and lets the user save again; a failed read shows the load error, does not
 * close the screen, and "Retry" reads the data again. The app itself starts on the list with the system theme.
 */
class DatabaseFailuresTest {

    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    @Test
    fun carListThatCouldNotBeReadIsReadAgainOnRetry() = runTest {
        app.addCar("Kia")
        app.hideTable("cars")
        val viewModel = app.viewModel<CarsViewModel>()
        watch(viewModel.state) { it.loadFailed }

        app.restoreTable("cars")
        viewModel.onRetry()

        val state = viewModel.state.first { !it.loadFailed && it.cars.isNotEmpty() }
        assertEquals(listOf("Kia"), state.cars.map { it.brand })
    }

    @Test
    fun failedViewChoiceRaisesTheBarAndKeepsTheStoredView() = runTest {
        val viewModel = app.viewModel<CarsViewModel>()
        watch(viewModel.state) { !it.isLoading }
        app.failWrites("settings")

        viewModel.onViewSelect(CarsView.TASKS_AND_HISTORY)

        viewModel.state.first { it.saveFailed }
        assertEquals(CarsView.LIST, app.settings().carsView)
        viewModel.onSaveFailureShown()
        viewModel.state.first { !it.saveFailed }
    }

    @Test
    fun carScreenThatCouldNotBeReadStaysOpen() = runTest {
        val kia = app.addCar("Kia")
        app.hideTable("cars")
        val viewModel = app.viewModel<CarDetailViewModel>(kia)

        val state = watch(viewModel.state) { it.loadFailed }

        assertFalse(state.isMissing)
        app.restoreTable("cars")
        viewModel.onRetry()
        assertEquals("Kia", viewModel.state.first { it.car != null }.car!!.brand)
    }

    @Test
    fun failedDoneMarkKeepsTheWindowOpenUntilItIsStored() = runTest {
        val kia = app.addCar("Kia", tasks = listOf(NewMaintenanceTask("Oil", 7_500, lastDoneKm = 90_000)))
        val viewModel = app.viewModel<CarDetailViewModel>(kia)
        val oil = watch(viewModel.state) { it.car != null }.car!!.tasks.single()
        viewModel.onMarkTaskDone(oil.id)
        app.failWrites("service_history")

        viewModel.onTaskDoneConfirm(serviceName = "Сервис", cost = "2500", comment = "")

        val failed = viewModel.state.first { it.saveFailed }
        assertEquals(oil.id, failed.taskDoneDialog?.id)
        assertEquals(90_000, app.car(kia)!!.tasks.single().lastDoneKm)
        app.allowWrites("service_history")
        viewModel.onTaskDoneConfirm(serviceName = "Сервис", cost = "2500", comment = "")
        val done = viewModel.state.first { it.taskDoneDialog == null && it.car!!.tasks.single().isDone }
        assertNull(done.taskDoneDialog)
    }

    @Test
    fun failedMileageKeepsTheEditOpen() = runTest {
        val kia = app.addCar("Kia")
        val viewModel = app.viewModel<CarDetailViewModel>(kia)
        watch(viewModel.state) { it.car != null }
        viewModel.onEdit(EditableField.MILEAGE)
        app.failWrites("cars")

        viewModel.onSaveMileage("120000")

        val state = viewModel.state.first { it.saveFailed }
        assertEquals(EditableField.MILEAGE, state.editingField)
        assertEquals(TestApp.MILEAGE_KM, app.car(kia)!!.mileageKm)
    }

    @Test
    fun failedTaskSaveKeepsTheFormAndCanBeRepeated() = runTest {
        val kia = app.addCar("Kia")
        val viewModel = app.viewModel<TaskFormViewModel>(kia, null)
        watch(viewModel.state) { !it.isLoading }
        viewModel.onFieldsChange(viewModel.state.value.fields.copy(name = "Колодки", intervalKm = "30000"))
        app.failWrites("maintenance_tasks")

        viewModel.onSave()

        val failed = viewModel.state.first { it.saveFailed }
        assertFalse(failed.isClosed)
        assertEquals("Колодки", failed.fields.name)
        app.allowWrites("maintenance_tasks")
        viewModel.onSave()
        viewModel.state.first { it.isClosed }
        assertEquals("Колодки", app.car(kia)!!.tasks.single().name)
    }

    @Test
    fun taskFormThatCouldNotReadTheCarIsReadAgainOnRetry() = runTest {
        val kia = app.addCar("Kia")
        app.hideTable("cars")
        val viewModel = app.viewModel<TaskFormViewModel>(kia, null)
        val failed = watch(viewModel.state) { it.loadFailed }
        assertFalse(failed.isClosed)

        app.restoreTable("cars")
        viewModel.onRetry()

        assertEquals("Kia", viewModel.state.first { !it.isLoading && !it.loadFailed }.carBrand)
    }

    @Test
    fun failedCarSaveKeepsTheFormAndCanBeRepeated() = runTest {
        val viewModel = app.viewModel<CarFormViewModel>()
        viewModel.onBrandChange("Kia")
        viewModel.onMileageChange("84300")
        app.failWrites("cars")

        viewModel.onSave("Замена масла")

        val failed = viewModel.state.first { it.saveFailed }
        assertFalse(failed.isSaved)
        assertEquals("Kia", failed.brand)
        app.allowWrites("cars")
        viewModel.onSave("Замена масла")
        viewModel.state.first { it.isSaved }
    }

    @Test
    fun failedRecordKeepsTheWindowOpenUntilItIsStored() = runTest {
        app.addCar("Kia")
        val viewModel = app.viewModel<HistoryViewModel>(null)
        watch(viewModel.state) { it.cars.isNotEmpty() }
        viewModel.onNewEntryOpen()
        viewModel.onNewEntryChange(viewModel.state.value.newEntry!!.copy(taskName = "Шины", date = "15.09.2026", mileage = "90000"))
        app.failWrites("service_history")

        viewModel.onNewEntrySave()

        assertNotNull(viewModel.state.first { it.saveFailed }.newEntry)
        app.allowWrites("service_history")
        viewModel.onNewEntrySave()
        viewModel.state.first { it.newEntry == null }
        assertEquals(LocalDate(2026, 9, 15), app.records { it.isNotEmpty() }.single().date)
    }

    @Test
    fun failedSettingRaisesTheBar() = runTest {
        val viewModel = app.viewModel<SettingsViewModel>()
        watch(viewModel.state) { it.settings != null }
        app.failWrites("settings")

        viewModel.onThemeSelect(ThemeMode.DARK)

        viewModel.state.first { it.saveFailed }
        assertEquals(ThemeMode.SYSTEM, app.settings().themeMode)
    }

    @Test
    fun settingsThatCouldNotBeReadAreReadAgainOnRetry() = runTest {
        app.hideTable("settings")
        val viewModel = app.viewModel<SettingsViewModel>()
        watch(viewModel.state) { it.loadFailed }

        app.restoreTable("settings")
        viewModel.onRetry()

        viewModel.state.first { it.settings != null && !it.loadFailed }
    }

    @Test
    fun appStartsOnTheListWithTheSystemThemeWhenSettingsCannotBeRead() = runTest {
        app.hideTable("settings")
        val viewModel = app.viewModel<AppViewModel>()
        keep(viewModel.themeMode)

        assertEquals(Launch.Ready(null), viewModel.launch.first { it is Launch.Ready })
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }
}
