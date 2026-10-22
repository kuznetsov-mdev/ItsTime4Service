package ru.kuznetsov.itstime4service.features.cardetail

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.model.TaskStatus
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.TestApp.Companion.MILEAGE_KM
import ru.kuznetsov.itstime4service.features.TestApp.Companion.TODAY
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.features.watch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [CarDetailViewModel] on the app's own graph and a database in memory: the task list and its counts, editing
 * the header values one at a time, the mileage typed and stepped in the chosen unit, marking a task done and taking
 * the mark back, hiding the plate and the VIN, and deleting the car.
 */
class CarDetailViewModelTest {

    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    private fun task(name: String, remainingKm: Int) = NewMaintenanceTask(
        name = name,
        intervalKm = INTERVAL_KM,
        lastDoneKm = MILEAGE_KM + remainingKm - INTERVAL_KM,
        reminderWindowKm = WINDOW_KM,
    )

    private suspend fun TestScope.open(carId: String): CarDetailViewModel {
        val viewModel = app.viewModel<CarDetailViewModel>(carId)
        watch(viewModel.state) { !it.isLoading }
        return viewModel
    }

    private suspend fun CarDetailViewModel.until(predicate: (CarDetailUiState) -> Boolean) = state.first(predicate)

    private suspend fun kia(vararg tasks: NewMaintenanceTask, mileageKm: Int = MILEAGE_KM) =
        app.addCar("Kia Rio", mileageKm = mileageKm, tasks = tasks.toList(), plate = "А123ВС77", vin = "XWEPC811BB0012345")

    private fun CarDetailUiState.task(name: String): TaskItem = car!!.tasks.first { it.name == name }

    @Test
    fun tasksAreListedMostUrgentFirstWithTheirCounts() = runTest {
        val id = kia(task("Belt", 5_000), task("Oil", -300), task("Filter", 500))

        val car = open(id).state.value.car!!

        assertEquals(listOf("Oil", "Filter", "Belt"), car.tasks.map { it.name })
        assertEquals(listOf(TaskStatus.DUE, TaskStatus.SOON, TaskStatus.OK), car.tasks.map { it.status })
        assertEquals(1, car.dueCount)
        assertEquals(1, car.soonCount)
        assertEquals(3, car.taskCount)
    }

    @Test
    fun openingTheScreenRemembersTheCar() = runTest {
        val id = kia()

        open(id)

        assertEquals(id, app.settings { it.lastOpenedCarId != null }.lastOpenedCarId)
    }

    @Test
    fun unknownCarClosesTheScreen() = runTest {
        assertTrue(open("missing").state.value.isMissing)
    }

    @Test
    fun savedBrandIsTrimmedAndEndsTheEdit() = runTest {
        val viewModel = open(kia())

        viewModel.onEdit(EditableField.BRAND)
        assertEquals(EditableField.BRAND, viewModel.state.value.editingField)
        viewModel.onSaveBrand("  Kia Ceed ")

        val car = viewModel.until { it.car?.brand == "Kia Ceed" && it.editingField == null }.car!!
        assertEquals("А123ВС77", car.plate)
        assertEquals("XWEPC811BB0012345", car.vin)
    }

    @Test
    fun cancelledEditLeavesTheValue() = runTest {
        val viewModel = open(kia())

        viewModel.onEdit(EditableField.VIN)
        viewModel.onCancelEdit()

        assertNull(viewModel.state.value.editingField)
        assertEquals("XWEPC811BB0012345", viewModel.state.value.car!!.vin)
    }

    @Test
    fun hiddenPlateAndVinAreNotEdited() = runTest {
        val id = kia()
        val viewModel = open(id)

        viewModel.onEdit(EditableField.PLATE)
        viewModel.onPlateHiddenToggle()
        assertNull(viewModel.state.value.editingField)
        viewModel.onVinHiddenToggle()
        viewModel.until { it.car!!.plateHidden && it.car.vinHidden }

        viewModel.onEdit(EditableField.PLATE)
        assertNull(viewModel.state.value.editingField)
        viewModel.onEdit(EditableField.VIN)
        assertNull(viewModel.state.value.editingField)
        viewModel.onEdit(EditableField.BRAND)
        assertEquals(EditableField.BRAND, viewModel.state.value.editingField)
        assertTrue(app.car(id)!!.plateHidden)
    }

    @Test
    fun mileageTypedInMilesKeepsItsKilometersWhenLeftAsItWas() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        val id = kia()
        val viewModel = open(id)
        viewModel.until { it.distanceUnit == DistanceUnit.MILES }

        viewModel.onEdit(EditableField.MILEAGE)
        viewModel.onSaveMileage(" 62137 ")
        viewModel.until { it.editingField == null }
        assertEquals(MILEAGE_KM, app.car(id)!!.mileageKm)

        viewModel.onSaveMileage("62200")
        assertEquals(100_101, viewModel.until { it.car?.mileageKm != MILEAGE_KM }.car!!.mileageKm)
    }

    @Test
    fun mileageStepsAreAppliedOneAfterAnother() = runTest {
        val viewModel = open(kia(mileageKm = 1_000))

        viewModel.onMultiplierSelect(2)
        repeat(3) { viewModel.onMileageStep(1) }
        viewModel.onMileageStep(-1)

        assertEquals(2, viewModel.state.value.mileageMultiplier)
        viewModel.until { it.car?.mileageKm == 1_400 }
    }

    @Test
    fun stepNeverTakesTheMileageToZero() = runTest {
        val viewModel = open(kia(mileageKm = 100))

        viewModel.onMileageStep(-1)
        viewModel.onMultiplierSelect(5)
        viewModel.onMileageStep(1)

        viewModel.until { it.car?.mileageKm == 600 }
    }

    @Test
    fun taskDoneWindowStoresTheRecordAndShowsTheMark() = runTest {
        val id = kia(task("Oil", -300))
        val viewModel = open(id)
        val oil = viewModel.state.value.task("Oil")

        viewModel.onMarkTaskDone(oil.id)
        assertEquals(oil.id, viewModel.state.value.taskDoneDialog?.id)
        viewModel.onTaskDoneConfirm(serviceName = "Сервис", cost = "2500", comment = " ")
        assertNull(viewModel.state.value.taskDoneDialog)

        val done = viewModel.until { it.task("Oil").isDone }.task("Oil")
        assertEquals(INTERVAL_KM, done.remainingKm)
        assertEquals(TaskStatus.OK, done.status)
        val record = app.records { it.isNotEmpty() }.single()
        assertEquals(TODAY, record.date)
        assertEquals(MILEAGE_KM, record.mileageKm)
        assertEquals("Сервис", record.serviceName)
        assertEquals(2500, record.costRub)
        assertNull(record.comment)
    }

    @Test
    fun dismissedTaskDoneWindowStoresNothing() = runTest {
        val viewModel = open(kia(task("Oil", -300)))

        viewModel.onMarkTaskDone(viewModel.state.value.task("Oil").id)
        viewModel.onTaskDoneDismiss()
        viewModel.onTaskDoneConfirm(serviceName = "", cost = "", comment = "")

        assertNull(viewModel.state.value.taskDoneDialog)
        assertTrue(app.records().isEmpty())
    }

    @Test
    fun confirmedUnmarkRollsTheTaskBackAndDeletesTheRecord() = runTest {
        val viewModel = open(kia(task("Oil", -300)))
        val oilId = viewModel.state.value.task("Oil").id
        viewModel.onMarkTaskDone(oilId)
        viewModel.onTaskDoneConfirm(serviceName = "", cost = "", comment = "")
        viewModel.until { it.task("Oil").isDone }

        viewModel.onUnmarkRequest(oilId)
        assertTrue(viewModel.state.value.isConfirmingUnmark)
        viewModel.onUnmarkConfirm()
        assertFalse(viewModel.state.value.isConfirmingUnmark)

        val oil = viewModel.until { !it.task("Oil").isDone }.task("Oil")
        assertEquals(-300, oil.remainingKm)
        app.records { it.isEmpty() }
    }

    @Test
    fun mileageChangeEndsTheDoneMark() = runTest {
        val viewModel = open(kia(task("Oil", -300)))
        viewModel.onMarkTaskDone(viewModel.state.value.task("Oil").id)
        viewModel.onTaskDoneConfirm(serviceName = "", cost = "", comment = "")
        viewModel.until { it.task("Oil").isDone }

        viewModel.onMileageStep(1)

        val oil = viewModel.until { it.car?.mileageKm == MILEAGE_KM + 100 }.task("Oil")
        assertFalse(oil.isDone)
        assertEquals(1, app.records().size)
    }

    @Test
    fun deletedCarWithHistoryStaysAsSold() = runTest {
        val id = kia(task("Oil", -300))
        val viewModel = open(id)
        viewModel.onMarkTaskDone(viewModel.state.value.task("Oil").id)
        viewModel.onTaskDoneConfirm(serviceName = "", cost = "", comment = "")
        viewModel.until { it.task("Oil").isDone }

        viewModel.onDeleteRequest()
        assertTrue(viewModel.state.value.isConfirmingDelete)
        viewModel.onDeleteConfirm()

        assertTrue(viewModel.until { it.isMissing }.isMissing)
        assertTrue(app.summaries().single { it.id == id }.isSold)
        assertEquals(1, app.records().size)
    }

    @Test
    fun deletedCarWithoutHistoryIsGone() = runTest {
        val viewModel = open(kia())

        viewModel.onDeleteRequest()
        viewModel.onDeleteDismiss()
        assertFalse(viewModel.state.value.isConfirmingDelete)
        viewModel.onDeleteRequest()
        viewModel.onDeleteConfirm()

        viewModel.until { it.isMissing }
        assertTrue(app.summaries().isEmpty())
    }

    private companion object {
        const val INTERVAL_KM = 10_000
        const val WINDOW_KM = 1_000
    }
}
