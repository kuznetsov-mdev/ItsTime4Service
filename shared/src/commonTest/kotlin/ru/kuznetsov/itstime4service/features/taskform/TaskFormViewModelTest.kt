package ru.kuznetsov.itstime4service.features.taskform

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.NewTaskRecord
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.features.watch
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [TaskFormViewModel] on the app's own graph and a database in memory: adding a task with errors, presets,
 * "add another" and a record of the last service, editing in the chosen unit, the icon window and deleting a task.
 * Dates are typed in the Russian format `dd.MM.yyyy`.
 */
class TaskFormViewModelTest {

    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    private suspend fun TestScope.open(carId: String, taskId: String? = null): TaskFormViewModel {
        val viewModel = app.viewModel<TaskFormViewModel>(carId, taskId)
        watch(viewModel.state) { !it.isLoading }
        return viewModel
    }

    private suspend fun TaskFormViewModel.until(predicate: (TaskFormUiState) -> Boolean) = state.first(predicate)

    private fun TaskFormViewModel.type(change: (TaskFields) -> TaskFields) = onFieldsChange(change(state.value.fields))

    @Test
    fun newTaskStartsEmptyUnderTheCarName() = runTest {
        val state = open(app.addCar("Kia Rio")).state.value

        assertEquals("Kia Rio", state.carBrand)
        assertFalse(state.isEditing)
        assertEquals(TaskFields(reminderWindowKm = "2000"), state.fields)
    }

    @Test
    fun newTaskUsesTheConfiguredReminderWindowInTheChosenUnit() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES, defaultReminderWindowKm = 3_219) }
        val kia = app.addCar("Kia")
        val viewModel = open(kia)

        assertEquals("2000", viewModel.state.value.fields.reminderWindowKm)
        viewModel.type { it.copy(name = "Oil", intervalKm = "10000") }
        viewModel.onSave()

        viewModel.until { it.isClosed }
        assertEquals(3_219, app.car(kia)!!.tasks.single().reminderWindowKm)
    }

    @Test
    fun unknownCarOrTaskClosesTheForm() = runTest {
        val kia = app.addCar("Kia")

        assertTrue(open("missing").state.value.isClosed)
        assertTrue(open(kia, "missing").state.value.isClosed)
    }

    @Test
    fun saveWithoutRequiredFieldsShowsErrorsAndStoresNothing() = runTest {
        val kia = app.addCar("Kia")
        val viewModel = open(kia)

        viewModel.type { it.copy(name = "Колодки") }
        viewModel.onSave()

        assertTrue(viewModel.state.value.showErrors)
        assertFalse(viewModel.state.value.isClosed)
        assertTrue(app.car(kia)!!.tasks.isEmpty())
    }

    @Test
    fun presetFillsTheFormInTheChosenUnit() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        val viewModel = open(app.addCar("Kia"))

        viewModel.type { it.copy(lastDoneKm = "50000") }
        viewModel.onPreset(TaskPreset.OIL, "Замена масла")

        val fields = viewModel.state.value.fields
        assertEquals("Замена масла", fields.name)
        assertEquals("4660", fields.intervalKm)
        assertEquals("932", fields.reminderWindowKm)
        assertEquals("50000", fields.lastDoneKm)
    }

    @Test
    fun typedNumbersKeepOnlyDigits() = runTest {
        val viewModel = open(app.addCar("Kia"))

        viewModel.type { it.copy(intervalKm = "10 000 км", lastDoneDate = "01.03.2026", cost = "12345678") }

        val fields = viewModel.state.value.fields
        assertEquals("10000", fields.intervalKm)
        assertEquals("01032026", fields.lastDoneDate)
        assertEquals("1234567", fields.cost)
    }

    @Test
    fun savedTaskClosesTheForm() = runTest {
        val kia = app.addCar("Kia")
        val viewModel = open(kia)

        viewModel.type { it.copy(name = "Колодки", intervalKm = "30000") }
        viewModel.onSave()
        viewModel.onSave()

        viewModel.until { it.isClosed }
        val task = app.car(kia)!!.tasks.single()
        assertEquals("Колодки", task.name)
        assertEquals(30_000, task.intervalKm)
        assertEquals(0, task.lastDoneKm)
        assertEquals(2_000, task.reminderWindowKm)
    }

    @Test
    fun addAnotherStoresTheTaskAndStartsAnEmptyForm() = runTest {
        val kia = app.addCar("Kia")
        val viewModel = open(kia)

        viewModel.type { it.copy(name = "Колодки", intervalKm = "30000") }
        viewModel.onSaveAndAddAnother()

        val state = viewModel.until { it.formKey == 1 }
        assertEquals(TaskFields(reminderWindowKm = "2000"), state.fields)
        assertFalse(state.showErrors)
        assertFalse(state.isClosed)
        assertEquals(listOf("Колодки"), app.car(kia)!!.tasks.map { it.name })
    }

    @Test
    fun recordOfTheLastServiceStartsTodayAndIsStoredWithTheTask() = runTest {
        val kia = app.addCar("Kia")
        val viewModel = open(kia)

        viewModel.type { it.copy(name = "Масло", intervalKm = "7500", intervalMonths = "12", lastDoneKm = "95000") }
        viewModel.onRecordToggle(true)
        assertEquals("21102026", viewModel.state.value.fields.lastDoneDate)
        viewModel.type { it.copy(lastDoneDate = "01.10.2026", serviceName = "Сервис", cost = "3000") }
        viewModel.onSave()

        viewModel.until { it.isClosed }
        val task = app.car(kia)!!.tasks.single()
        assertEquals(LocalDate(2026, 10, 1), task.lastDoneDate)
        val record = app.records { it.isNotEmpty() }.single()
        assertEquals("Масло", record.taskName)
        assertEquals(LocalDate(2026, 10, 1), record.date)
        assertEquals(95_000, record.mileageKm)
        assertEquals("Сервис", record.serviceName)
        assertEquals(3_000, record.costRub)
    }

    @Test
    fun recordToggleKeepsATypedDate() = runTest {
        val viewModel = open(app.addCar("Kia"))

        viewModel.type { it.copy(lastDoneDate = "01102026") }
        viewModel.onRecordToggle(true)
        viewModel.onRecordToggle(false)

        assertEquals("01102026", viewModel.state.value.fields.lastDoneDate)
        assertFalse(viewModel.state.value.fields.createsRecord)
    }

    @Test
    fun editedTaskKeepsTheKilometersOfDistancesLeftAsTheyWere() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        val kia = app.addCar("Kia", tasks = listOf(NewMaintenanceTask("Масло", 10_000, 91_000, 1_000)))
        val taskId = app.car(kia)!!.tasks.single().id
        val viewModel = open(kia, taskId)

        val fields = viewModel.state.value.fields
        assertTrue(viewModel.state.value.isEditing)
        assertEquals("6214", fields.intervalKm)
        assertEquals("56545", fields.lastDoneKm)
        viewModel.type { it.copy(name = "Масло и фильтр", reminderWindowKm = "1000") }
        viewModel.onSave()

        viewModel.until { it.isClosed }
        val task = app.car(kia)!!.tasks.single()
        assertEquals("Масло и фильтр", task.name)
        assertEquals(10_000, task.intervalKm)
        assertEquals(91_000, task.lastDoneKm)
        assertEquals(1_609, task.reminderWindowKm)
    }

    @Test
    fun pickedIconClosesItsWindow() = runTest {
        val viewModel = open(app.addCar("Kia"))

        viewModel.onIconPickerOpen()
        assertTrue(viewModel.state.value.isPickingIcon)
        viewModel.onIconPickerDismiss()
        assertFalse(viewModel.state.value.isPickingIcon)
        viewModel.onIconPickerOpen()
        viewModel.onIconSelect(TaskIcon.TIRE)

        assertFalse(viewModel.state.value.isPickingIcon)
        assertEquals(TaskIcon.TIRE, viewModel.state.value.fields.icon)
    }

    @Test
    fun deletedTaskLeavesItsHistory() = runTest {
        val record = NewTaskRecord(LocalDate(2026, 3, 1), serviceName = null, costRub = null, comment = null)
        val kia = app.addCar("Kia", tasks = listOf(NewMaintenanceTask("Масло", 7_500, 90_000, record = record)))
        val viewModel = open(kia, app.car(kia)!!.tasks.single().id)

        viewModel.onDeleteRequest()
        assertTrue(viewModel.state.value.isConfirmingDelete)
        viewModel.onDeleteDismiss()
        assertFalse(viewModel.state.value.isConfirmingDelete)
        viewModel.onDeleteRequest()
        viewModel.onDeleteConfirm()

        viewModel.until { it.isClosed }
        assertTrue(app.car(kia)!!.tasks.isEmpty())
        assertEquals(1, app.records().size)
    }

    @Test
    fun deleteIsNotOfferedForANewTask() = runTest {
        val kia = app.addCar("Kia", tasks = listOf(NewMaintenanceTask("Масло", 7_500)))
        val viewModel = open(kia)

        viewModel.onDeleteRequest()
        viewModel.onDeleteConfirm()

        assertFalse(viewModel.state.value.isClosed)
        assertEquals(1, app.car(kia)!!.tasks.size)
    }
}
