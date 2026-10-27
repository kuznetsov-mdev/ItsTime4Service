package ru.kuznetsov.itstime4service.features.carform

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.ui.format.TaskFields
import ru.kuznetsov.itstime4service.ui.format.TaskPreset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [CarFormViewModel] on the app's own graph and a database in memory: task blocks added, collapsed and removed,
 * input cleaned as it is typed, errors that expand the broken blocks, the default oil change, distances in the chosen
 * unit and a record of a task's last service. Dates are typed in the Russian format `dd.MM.yyyy`.
 */
class CarFormViewModelTest {

    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    private fun open(): CarFormViewModel = app.viewModel()

    private suspend fun CarFormViewModel.until(predicate: (CarFormUiState) -> Boolean) = state.first(predicate)

    private fun CarFormViewModel.fill(brand: String = "Kia Rio", mileage: String = "84300") {
        onBrandChange(brand)
        onMileageChange(mileage)
    }

    private fun CarFormViewModel.block(index: Int): TaskBlockState = state.value.tasks[index]

    private fun CarFormViewModel.typeIn(index: Int, change: (TaskFields) -> TaskFields) =
        onTaskChange(block(index).key, change(block(index).fields))

    @Test
    fun blocksAppearExpandedAndCanBeCollapsedAndRemoved() = runTest {
        val viewModel = open()
        assertTrue(viewModel.state.value.tasks.isEmpty())

        viewModel.onAddTask()
        viewModel.onAddPreset(TaskPreset.PADS, "Колодки")
        val (first, second) = viewModel.state.value.tasks
        assertFalse(first.isCollapsed)
        assertEquals(TaskFields(), first.fields)
        assertEquals("Колодки", second.fields.name)
        assertEquals("30000", second.fields.intervalKm)
        assertTrue(first.key != second.key)

        viewModel.onToggleTask(second.key)
        assertTrue(viewModel.block(1).isCollapsed)
        viewModel.onToggleTask(second.key)
        assertFalse(viewModel.block(1).isCollapsed)

        viewModel.onRemoveTask(first.key)
        assertEquals(listOf(second.key), viewModel.state.value.tasks.map { it.key })
    }

    @Test
    fun addTaskAddsABlockOnlyOnceEveryBlockIsFilled() = runTest {
        val viewModel = open()

        viewModel.onAddTask()
        viewModel.onAddTask()

        assertEquals(1, viewModel.state.value.tasks.size)
        assertTrue(viewModel.block(0).showErrors)
        assertEquals(1, viewModel.block(0).revealRequest)
        assertFalse(viewModel.state.value.showErrors)

        viewModel.typeIn(0) { it.copy(name = "Колодки", intervalKm = "30000") }
        viewModel.onAddTask()

        assertEquals(2, viewModel.state.value.tasks.size)
        assertFalse(viewModel.block(1).showErrors)
    }

    @Test
    fun addTaskOutlinesEveryUnfinishedBlockWithoutExpandingIt() = runTest {
        val viewModel = open()
        viewModel.onAddPreset(TaskPreset.OIL, "Масло")
        viewModel.onAddPreset(TaskPreset.PADS, "Колодки")
        viewModel.onAddPreset(TaskPreset.AIR_FILTER, "Воздушный фильтр")
        viewModel.typeIn(0) { it.copy(name = "") }
        viewModel.typeIn(2) { it.copy(intervalKm = "") }
        viewModel.onToggleTask(viewModel.block(2).key)

        viewModel.onAddTask()

        val (first, second, third) = viewModel.state.value.tasks
        assertEquals(3, viewModel.state.value.tasks.size)
        assertTrue(first.showErrors && third.showErrors)
        assertFalse(second.showErrors)
        assertTrue(third.isCollapsed)
        assertEquals(listOf(1, 0, 0), listOf(first.revealRequest, second.revealRequest, third.revealRequest))
        assertFalse(viewModel.state.value.showErrors)
    }

    @Test
    fun presetChipsAddABlockEvenNextToAnUnfinishedOne() = runTest {
        val viewModel = open()

        viewModel.onAddTask()
        viewModel.onAddPreset(TaskPreset.OIL, "Масло")

        assertEquals(2, viewModel.state.value.tasks.size)
        assertFalse(viewModel.block(0).showErrors)
    }

    @Test
    fun inputIsCleanedAsItIsTyped() = runTest {
        val viewModel = open()

        viewModel.onMileageChange("84 300 км")
        viewModel.onVinChange("xwepc811bb0012345678")
        viewModel.onPlateChange("а123вс77rus-extra")

        val state = viewModel.state.value
        assertEquals("84300", state.mileageKm)
        assertEquals("XWEPC811BB0012345", state.vin)
        assertEquals("А123ВС77RUS-", state.plate)
    }

    @Test
    fun saveWithErrorsStoresNothingAndExpandsOnlyBrokenBlocks() = runTest {
        val viewModel = open()
        viewModel.fill()
        viewModel.onAddPreset(TaskPreset.OIL, "Масло")
        viewModel.onAddTask()
        viewModel.typeIn(1) { it.copy(name = "Колодки") }
        viewModel.state.value.tasks.forEach { viewModel.onToggleTask(it.key) }

        viewModel.onSave("Замена масла")

        val state = viewModel.state.value
        assertTrue(state.showErrors)
        assertFalse(state.isSaved)
        assertTrue(state.tasks[0].isCollapsed)
        assertFalse(state.tasks[1].isCollapsed)
        assertTrue(app.summaries().isEmpty())
    }

    @Test
    fun carWithoutBlocksGetsTheDefaultOilChange() = runTest {
        val viewModel = open()
        viewModel.fill()
        viewModel.onPlateChange("а123вс77")

        viewModel.onSave("Замена масла")
        viewModel.onSave("Замена масла")

        viewModel.until { it.isSaved }
        val car = app.cars.observeCars().first().single()
        assertEquals("Kia Rio", car.brand)
        assertEquals(84_300, car.mileageKm)
        assertEquals("А123ВС77", car.plate)
        val task = car.tasks.single()
        assertEquals("Замена масла", task.name)
        assertEquals(TaskPreset.OIL.intervalKm, task.intervalKm)
        assertEquals(84_300, task.lastDoneKm)
        assertEquals(TaskPreset.OIL.reminderWindowKm, task.reminderWindowKm)
    }

    @Test
    fun distancesAreTypedInTheChosenUnitAndStoredInKilometers() = runTest {
        app.changeSettings { it.copy(distanceUnit = DistanceUnit.MILES) }
        val viewModel = open()
        viewModel.until { it.distanceUnit == DistanceUnit.MILES }
        viewModel.fill(mileage = "10000")
        viewModel.onAddTask()
        viewModel.typeIn(0) { it.copy(name = "Масло", intervalKm = "5000", lastDoneKm = "8000") }

        viewModel.onSave("Замена масла")

        viewModel.until { it.isSaved }
        val car = app.cars.observeCars().first().single()
        assertEquals(16_093, car.mileageKm)
        assertEquals(8_047, car.tasks.single().intervalKm)
        assertEquals(12_875, car.tasks.single().lastDoneKm)
    }

    @Test
    fun blockWithARecordStoresTheLastServiceInTheHistory() = runTest {
        val viewModel = open()
        viewModel.fill()
        viewModel.onAddTask()
        viewModel.typeIn(0) { it.copy(name = "Масло", intervalKm = "7500", lastDoneKm = "80000") }
        viewModel.onTaskRecordToggle(viewModel.block(0).key, true)
        assertEquals("21102026", viewModel.block(0).fields.lastDoneDate)
        viewModel.typeIn(0) { it.copy(lastDoneDate = "01.06.2026", comment = "Синтетика") }

        viewModel.onSave("Замена масла")

        viewModel.until { it.isSaved }
        val record = app.records { it.isNotEmpty() }.single()
        assertEquals("Масло", record.taskName)
        assertEquals(LocalDate(2026, 6, 1), record.date)
        assertEquals(80_000, record.mileageKm)
        assertEquals("Синтетика", record.comment)
    }
}
