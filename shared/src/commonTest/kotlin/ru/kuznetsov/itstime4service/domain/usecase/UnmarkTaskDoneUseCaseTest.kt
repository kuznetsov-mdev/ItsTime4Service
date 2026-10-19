package ru.kuznetsov.itstime4service.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [UnmarkTaskDoneUseCase]: the mark is looked up and rolled back by the storage in one transaction, and its
 * answer is passed on. Which record holds a mark is covered by [DoneMarksUseCaseTest].
 */
class UnmarkTaskDoneUseCaseTest {

    @Test
    fun markIsRolledBackByTheStorage() = runTest {
        val repository = FakeTaskHistoryRepository()

        assertTrue(UnmarkTaskDoneUseCase(repository)("car", "oil"))
        assertEquals(listOf("car" to "oil"), repository.unmarked)
    }

    @Test
    fun taskThatIsNotMarkedIsReported() = runTest {
        assertFalse(UnmarkTaskDoneUseCase(FakeTaskHistoryRepository(result = false))("car", "oil"))
    }
}
