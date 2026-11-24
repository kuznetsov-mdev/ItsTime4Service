package ru.kuznetsov.itstime4service.features.transfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.data.transfer.TransferCodecImpl
import ru.kuznetsov.itstime4service.data.transfer.TransferDocumentCodecImpl
import ru.kuznetsov.itstime4service.data.transfer.TransferFixtures
import ru.kuznetsov.itstime4service.domain.model.NewMaintenanceTask
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.features.TestApp
import ru.kuznetsov.itstime4service.features.ViewModelTestSetup
import ru.kuznetsov.itstime4service.features.watch
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import ru.kuznetsov.itstime4service.ui.transfer.*
import kotlin.test.*

/** Exercises import decisions and repeated matching with the production transfer graph. */
@OptIn(ExperimentalTestApi::class)
class TransferImportRegressionTest : UiTest() {
    private val app = TestApp()
    private val files = FakeTransferFiles()
    private val documents = TransferDocumentCodecImpl(TransferCodecImpl(), transferFileCheckCipher())
    private val syntheticPassword = "Synthetic import password"

    @BeforeTest fun start() {
        ViewModelTestSetup.start()
        app.koin.declare<TransferFiles>(files)
        app.koin.declare<TransferCipher>(transferFileCheckCipher())
    }
    @AfterTest fun close() { app.close(); ViewModelTestSetup.finish() }

    private suspend fun TestScope.open(): TransferViewModel {
        val model = app.viewModel<TransferViewModel>(TransferEntry.Settings)
        model.start()
        watch(model.state) { !it.isLoading && !it.isBusy }
        return model
    }

    private suspend fun TransferViewModel.ready() = state.first { !it.isBusy }

    @Test fun personalDecisionsSurviveResolvingAnAmbiguousTask() = runTest {
        val targetId = app.addCar("Synthetic car", 43000, listOf(
            NewMaintenanceTask("Oil", 8000),
            NewMaintenanceTask("Oil", 16000),
        ), vin = "LOCAL-TEST")
        val base = TransferFixtures.car()
        val incoming = base.cars.single().copy(brand = "Synthetic car",
            mileageKm = Presence.Present(63000), vin = Presence.Present("INCOMING-TEST"))
        val snapshot = base.copy(cars = listOf(incoming), includedGroups = setOf(TransferGroup.MILEAGE, TransferGroup.VIN))
        files.input = SecretBytes.fromPassword(syntheticPassword).use { secret ->
            assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(snapshot, secret)).value
        }

        val model = open()
        model.onStartImport()
        model.onPickFile()
        assertEquals(TransferStep.UNLOCK, model.ready().step)
        model.onUnlock(syntheticPassword)
        assertEquals(TransferStep.IMPORT_SETUP, model.ready().step)
        val ref = EntityRef(incoming.id, incoming.tasks.single().id)
        model.onTargetDraft(incoming.id, ImportTargetDraft(existingId = targetId))
        model.onTaskChoice(ref, TaskChoice.UPDATE_PARAMETERS)
        model.onPrepareImport()
        val unresolved = model.ready()
        assertEquals(TransferStep.IMPORT_SETUP, unresolved.step)
        assertTrue(unresolved.pending.any { it.reason == ImportDecisionReason.TASK_TARGET })
        assertTrue(unresolved.pending.filter { it.reason == ImportDecisionReason.PERSONAL_FIELD }.map { it.field }.containsAll(listOf("mileage", "vin")))

        val chosen = app.car(targetId)!!.tasks.single { it.intervalKm == 16000 }
        model.onTaskTarget(ref, chosen.id)
        model.onPrepareImport()
        val next = model.ready()
        assertEquals(TransferStep.IMPORT_DECISIONS, next.step)
        model.onPersonalChoice(next.pending.single { it.field == "vin" }, PersonalChoice.TAKE_INCOMING)
        model.onBack()
        assertEquals(TransferStep.IMPORT_SETUP, model.ready().step)
        model.onPrepareImport()
        val remaining = model.ready()
        assertEquals(TransferStep.IMPORT_DECISIONS, remaining.step)
        assertTrue(remaining.pending.any { it.field == "mileage" })
        model.onPrepareImport()
        val preview = model.ready()
        assertEquals(TransferStep.IMPORT_PREVIEW, preview.step)
        assertEquals(chosen.id, preview.importPreview!!.taskTargets.getValue(ref).id)
        val car = preview.importPreview.cars.single()
        assertEquals(Presence.Present("INCOMING-TEST"), car.vin)
        assertEquals(Presence.Present(43000), car.mileageKm)
    }

    @Test fun aPreviouslyImportedRenamedTaskOffersAllMatchingActions() {
        lateinit var reopened: TransferUiState
        runTest {
            val base = TransferFixtures.car()
            val incoming = base.cars.single().copy(maintenanceDescription = null)
            val sourceSnapshot = base.copy(cars = listOf(incoming))
            val localId = app.addCar(incoming.brand)
            val otherCarId = app.addCar("Unmatched destination")
            files.input = assertIs<TransferOutcome.Success<ByteArray>>(documents.encode(sourceSnapshot, null)).value
            val first = open()
            first.onStartImport()
            first.onPickFile()
            assertEquals(TransferStep.IMPORT_SETUP, first.ready().step)
            first.onTargetDraft(incoming.id, ImportTargetDraft(existingId = localId))
            first.onPrepareImport()
            assertEquals(TransferStep.IMPORT_PREVIEW, first.ready().step)
            first.onApplyImport()
            assertEquals(TransferStep.RESULT, first.ready().step)
            val task = app.car(localId)!!.tasks.single()
            app.taskHistory.updateTask(localId, task.copy(name = "Renamed synthetic work"), null)
            assertEquals("Renamed synthetic work", app.car(localId)!!.tasks.single().name)
            first.onBack()

            val second = open()
            second.onStartImport()
            second.onPickFile()
            assertEquals(TransferStep.IMPORT_SETUP, second.ready().step)
            second.onTargetDraft(incoming.id, ImportTargetDraft(existingId = otherCarId))
            assertTrue(second.ready().previousTaskMatches.keys.none { it.targetCarId == otherCarId })
            second.onTargetDraft(incoming.id, ImportTargetDraft(existingId = localId))
            reopened = second.ready()
            assertEquals("Renamed synthetic work", reopened.cars.single { it.id == localId }.tasks.single().name)
            second.onPrepareImport()
            val preview = second.ready()
            assertEquals(TransferStep.IMPORT_PREVIEW, preview.step)
            assertEquals(1, preview.importSummary!!.skipped)
            assertTrue(preview.pending.isEmpty())

            val ref = EntityRef(incoming.id, incoming.tasks.single().id)
            second.onBack()
            second.onTaskChoice(ref, TaskChoice.ADD_SEPARATE)
            second.onPrepareImport()
            val separate = second.ready()
            assertEquals(1, separate.importSummary!!.tasksAdded)
            assertEquals(2, separate.importPreview!!.cars.single { it.id == localId }.tasks.size)

            second.onBack()
            second.onTaskChoice(ref, TaskChoice.UPDATE_PARAMETERS)
            second.onPrepareImport()
            val updated = second.ready()
            assertEquals(1, updated.importSummary!!.tasksUpdated)
            assertEquals(task.id, updated.importPreview!!.taskTargets.getValue(ref).id)
            assertEquals(incoming.tasks.single().name,
                updated.importPreview.cars.single { it.id == localId }.tasks.single().name)
        }

        runComposeUiTest {
            lateinit var skip: String
            lateinit var update: String
            lateinit var add: String
            setContent {
                skip = stringResource(Res.string.transfer_skip)
                update = stringResource(Res.string.transfer_update_task)
                add = stringResource(Res.string.transfer_add_task)
                ItsTime4ServiceTheme { TransferContent(reopened, TransferActions()) }
            }
            onNode(hasText(skip) and isSelectable()).performScrollTo().assertIsSelected()
            onNode(hasText(update) and isSelectable()).performScrollTo().assertIsDisplayed()
            onNode(hasText(add) and isSelectable()).performScrollTo().assertIsDisplayed()
        }
    }
}
