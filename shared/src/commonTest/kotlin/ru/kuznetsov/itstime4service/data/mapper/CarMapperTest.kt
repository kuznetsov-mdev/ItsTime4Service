package ru.kuznetsov.itstime4service.data.mapper

import ru.kuznetsov.itstime4service.data.local.Cars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers how a stored car row becomes a [ru.kuznetsov.itstime4service.domain.model.Car]: the plate and whether the
 * plate and the VIN are hidden.
 */
class CarMapperTest {

    private fun row(plateHidden: Boolean, vinHidden: Boolean) = Cars(
        id = "kia",
        brand = "Kia Rio",
        mileage_km = 84_300,
        vin = "XWEPC811BB0012345",
        is_sold = false,
        plate = "А123ВС77",
        plate_hidden = plateHidden,
        vin_hidden = vinHidden,
    )

    @Test
    fun hiddenFlagsAreRead() {
        val car = row(plateHidden = true, vinHidden = false).toDomain(emptyList())

        assertEquals("А123ВС77", car.plate)
        assertTrue(car.plateHidden)
        assertFalse(car.vinHidden)
    }

    @Test
    fun visibleCarHasNothingHidden() {
        val car = row(plateHidden = false, vinHidden = false).toDomain(emptyList())

        assertFalse(car.plateHidden)
        assertFalse(car.vinHidden)
    }
}
