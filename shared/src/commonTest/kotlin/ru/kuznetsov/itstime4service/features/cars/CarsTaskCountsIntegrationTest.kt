package ru.kuznetsov.itstime4service.features.cars

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.features.watch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies live car counters against the application graph and a real isolated SQLite database. */
class CarsTaskCountsIntegrationTest {
    private val app = TestApp()

    @BeforeTest
    fun setUp() = ViewModelTestSetup.start()

    @AfterTest
    fun tearDown() {
        app.close()
        ViewModelTestSetup.finish()
    }

    @Test
    fun listTracksTasksCreatedLaterAndTheirDeletion() = runTest {
        val carId = app.addCar("Test car", mileageKm = 10000, tasks = listOf(
            NewMaintenanceTask("Due", intervalKm = 10000, lastDoneKm = 0),
        ))
        val model = app.viewModel<CarsViewModel>()
        val initial = watch(model.state) { !it.isLoading }
        assertEquals(1, initial.cars.single().alertCount)
        assertEquals(1, initial.cars.single().taskCount)
        repeat(8) { index ->
            app.taskHistory.addTask(carId, NewMaintenanceTask("Later $index", intervalKm = 10000, lastDoneKm = 10000))
        }
        val added = model.state.first { it.cars.single().taskCount == 9 }.cars.single()
        assertEquals(1, added.alertCount)
        val tasks = app.car(carId)!!.tasks
        for (task in tasks.filter { it.name.startsWith("Later") }) app.cars.deleteTask(carId, task.id)
        val removed = model.state.first { it.cars.single().taskCount == 1 }.cars.single()
        assertEquals(1, removed.alertCount)
        app.cars.deleteTask(carId, tasks.single { it.name == "Due" }.id)
        val empty = model.state.first { it.cars.single().taskCount == 0 }.cars.single()
        assertEquals(0, empty.alertCount)
    }
}
