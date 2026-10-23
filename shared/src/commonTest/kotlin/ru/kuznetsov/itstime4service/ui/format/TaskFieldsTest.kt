package ru.kuznetsov.itstime4service.ui.format

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.DistanceUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [TaskFields] on their own: the last-done mileage and date are required only while a history record is
 * created, typed numbers are cut to seven digits, and the largest number in miles still fits in kilometers.
 * Dates are typed in the Russian format `dd.MM.yyyy`.
 */
class TaskFieldsTest {

    private val systemLanguage = DateInputFormat.language
    private val today = LocalDate(2026, 10, 21)
    private val oil = TaskFields(name = "Oil change", intervalKm = "7500")

    @BeforeTest
    fun setUp() {
        DateInputFormat.language = { "ru" }
    }

    @AfterTest
    fun tearDown() {
        DateInputFormat.language = systemLanguage
    }

    @Test
    fun withoutRecordOnlyNameAndIntervalAreRequired() {
        assertTrue(oil.isValid)
        val task = oil.toNewTask(DistanceUnit.KILOMETERS)!!
        assertEquals(0, task.lastDoneKm)
        assertNull(task.lastDoneDate)
        assertNull(task.record)
    }

    @Test
    fun recordMakesLastDoneMileageAndDateRequired() {
        val fields = oil.copy(createsRecord = true, lastDoneDate = "3102")

        assertTrue(fields.isLastDoneMissing)
        assertTrue(fields.isLastDoneDateMissing)
        assertNull(fields.toNewTask(DistanceUnit.KILOMETERS))
        assertTrue(fields.copy(lastDoneKm = "0").isLastDoneMissing)
    }

    @Test
    fun turningRecordOnFillsOnlyAnEmptyDateWithToday() {
        assertEquals("21102026", oil.withRecord(on = true, today = today).lastDoneDate)
        assertEquals("01032026", oil.copy(lastDoneDate = "01032026").withRecord(on = true, today = today).lastDoneDate)
    }

    @Test
    fun turningRecordOffLiftsTheRequirementAndKeepsTheDate() {
        val fields = oil.withRecord(on = true, today = today).withRecord(on = false, today = today)

        assertFalse(fields.isLastDoneMissing)
        assertEquals("21102026", fields.lastDoneDate)
        assertNull(fields.toNewTask(DistanceUnit.KILOMETERS)!!.record)
    }

    @Test
    fun recordCarriesItsDateMileageAndTexts() {
        val fields = oil.copy(
            lastDoneKm = "80000",
            lastDoneDate = "01032026",
            serviceName = "Kia Center",
            cost = "4200",
            comment = "Motul",
        ).withRecord(on = true, today = today)

        val record = fields.toNewTask(DistanceUnit.KILOMETERS)!!.record!!
        assertEquals(LocalDate(2026, 3, 1), record.date)
        assertEquals("Kia Center", record.serviceName)
        assertEquals(4_200, record.costRub)
        assertEquals("Motul", record.comment)
    }

    @Test
    fun typedNumbersAreCutToSevenDigits() {
        val fields = oil.copy(intervalKm = "12 345 678", lastDoneKm = "99999999", cost = "1a2b3").sanitized()

        assertEquals("1234567", fields.intervalKm)
        assertEquals("9999999", fields.lastDoneKm)
        assertEquals("123", fields.cost)
    }

    @Test
    fun largestNumberInMilesFitsInKilometers() {
        val task = oil.copy(intervalKm = "9999999", lastDoneKm = "9999999").toNewTask(DistanceUnit.MILES)!!

        assertEquals(16_093_438, task.intervalKm)
        assertEquals(9_999_999, task.intervalKm.kmIn(DistanceUnit.MILES))
    }
}
