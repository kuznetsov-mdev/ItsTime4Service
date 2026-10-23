package ru.kuznetsov.itstime4service.features

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import itstime4service.shared.generated.resources.Res
import itstime4service.shared.generated.resources.action_save
import itstime4service.shared.generated.resources.load_failed_title
import itstime4service.shared.generated.resources.retry
import itstime4service.shared.generated.resources.save_failed
import org.jetbrains.compose.resources.stringResource
import ru.kuznetsov.itstime4service.features.cars.CarsContent
import ru.kuznetsov.itstime4service.features.cars.CarsUiState
import ru.kuznetsov.itstime4service.features.history.HistoryCar
import ru.kuznetsov.itstime4service.features.history.HistoryContent
import ru.kuznetsov.itstime4service.features.history.HistoryUiState
import ru.kuznetsov.itstime4service.features.history.NewHistoryEntryForm
import ru.kuznetsov.itstime4service.features.taskform.TaskFormContent
import ru.kuznetsov.itstime4service.features.taskform.TaskFormUiState
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers how screens show database failures, from a ready state: the car list replaced by the load error whose "Retry"
 * reaches the view model, the "could not save" bar on the car list and above the save button of the task screen,
 * and the bar drawn once — inside the open record window, not under it.
 */
@OptIn(ExperimentalTestApi::class)
class DatabaseFailuresUiTest : UiTest() {

    @Test
    fun carListThatCouldNotBeReadOffersRetry() = runComposeUiTest {
        var title = ""
        var retry = ""
        var retries = 0
        setContent {
            title = stringResource(Res.string.load_failed_title)
            retry = stringResource(Res.string.retry)
            ItsTime4ServiceTheme(darkTheme = false) {
                CarsContent(
                    state = CarsUiState(isLoading = false, loadFailed = true),
                    onViewSelect = {},
                    onPlateToggle = {},
                    onOpenCar = {},
                    onOpenHistory = {},
                    onAddCar = {},
                    onRetry = { retries++ },
                )
            }
        }

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(retry).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun carListShowsTheSaveBar() = runComposeUiTest {
        var message = ""
        setContent {
            message = stringResource(Res.string.save_failed)
            ItsTime4ServiceTheme(darkTheme = false) {
                CarsContent(
                    state = CarsUiState(isLoading = false, saveFailed = true),
                    onViewSelect = {},
                    onPlateToggle = {},
                    onOpenCar = {},
                    onOpenHistory = {},
                    onAddCar = {},
                )
            }
        }

        onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun taskScreenShowsTheSaveBarAndKeepsSaving() = runComposeUiTest {
        var message = ""
        var save = ""
        var saves = 0
        setContent {
            message = stringResource(Res.string.save_failed)
            save = stringResource(Res.string.action_save)
            ItsTime4ServiceTheme(darkTheme = false) {
                TaskFormContent(
                    state = TaskFormUiState(isLoading = false, carBrand = "Kia", saveFailed = true),
                    onBack = {},
                    onFieldsChange = {},
                    onPreset = { _, _ -> },
                    onIconPickerOpen = {},
                    onRecordToggle = {},
                    onIconPickerDismiss = {},
                    onIconSelect = {},
                    onSave = { saves++ },
                    onSaveAndAddAnother = {},
                    onDeleteRequest = {},
                    onDeleteDismiss = {},
                    onDeleteConfirm = {},
                )
            }
        }

        onNodeWithText(message).assertIsDisplayed()
        onNodeWithText(save).performClick()

        assertEquals(1, saves)
    }

    @Test
    fun recordWindowShowsTheSaveBarOnce() = runComposeUiTest {
        var message = ""
        setContent {
            message = stringResource(Res.string.save_failed)
            ItsTime4ServiceTheme(darkTheme = false) {
                HistoryContent(
                    state = HistoryUiState(
                        isLoading = false,
                        cars = listOf(HistoryCar("kia", "Kia")),
                        newEntry = NewHistoryEntryForm(carId = "kia"),
                        saveFailed = true,
                    ),
                    onCarMenuToggle = {},
                    onCarMenuDismiss = {},
                    onCarSelect = {},
                    onFilterOpen = {},
                    onFilterChange = {},
                    onFilterApply = {},
                    onFilterReset = {},
                    onFilterDismiss = {},
                    onEntryOpen = {},
                    onEntryChange = {},
                    onEntrySave = {},
                    onEntryDismiss = {},
                    onNewEntryOpen = {},
                    onNewEntryChange = {},
                    onNewEntrySave = {},
                    onNewEntryDismiss = {},
                )
            }
        }

        onAllNodesWithText(message).assertCountEquals(1)
    }
}
