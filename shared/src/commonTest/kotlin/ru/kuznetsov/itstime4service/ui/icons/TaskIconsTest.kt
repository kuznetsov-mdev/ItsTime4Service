package ru.kuznetsov.itstime4service.ui.icons

import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [taskIcon]: a picked icon always wins; without one the icon is guessed from the task name
 * in any app language — oil, brakes, filters — and anything else gets a wrench.
 */
class TaskIconsTest {

    @Test
    fun pickedIconWinsOverName() {
        assertEquals(TaskIcon.TIRE, taskIcon(TaskIcon.TIRE, "Замена масла"))
    }

    @Test
    fun oilIsGuessedInEveryLanguage() {
        listOf("Замена масла и фильтра", "Oil change", "Cambio de aceite").forEach {
            assertEquals(TaskIcon.DROPLET, taskIcon(null, it))
        }
    }

    @Test
    fun brakesAreGuessedInEveryLanguage() {
        listOf("Тормозные колодки", "Brake pads", "Pastillas de freno").forEach {
            assertEquals(TaskIcon.DISC, taskIcon(null, it))
        }
    }

    @Test
    fun filtersAreGuessedInEveryLanguage() {
        listOf("Воздушный фильтр", "Cabin filter", "Filtro de aire").forEach {
            assertEquals(TaskIcon.FILTER, taskIcon(null, it))
        }
    }

    @Test
    fun guessIgnoresCase() {
        assertEquals(TaskIcon.DROPLET, taskIcon(null, "OIL"))
    }

    @Test
    fun unknownNameGetsWrench() {
        assertEquals(TaskIcon.WRENCH, taskIcon(null, "Ремень ГРМ"))
    }
}
