package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.datetime.LocalDate
import ru.kuznetsov.itstime4service.domain.model.Settings
import ru.kuznetsov.itstime4service.domain.model.TaskIcon
import ru.kuznetsov.itstime4service.domain.transfer.*
import kotlin.time.Instant

internal object TransferFixtures {
    fun id(number: Int): String = "00000000-0000-4000-8000-${number.toString().padStart(12, '0')}"

    fun car() = TransferSnapshot(
        1, id(100), Instant.parse("2026-11-10T12:00:00.000Z"), TransferKind.CAR, emptySet(),
        listOf(TransferCar("c1", "Test café 🔧", "Author's template", Presence.Absent, Presence.Absent,
            Presence.Absent, Presence.Absent, Presence.Absent,
            listOf(TransferTask("t1", "Oil", 10000, 12, TaskIcon.DROPLET, Presence.Absent, Presence.Absent)), Presence.Absent)),
        Presence.Absent, Presence.Absent,
    )

    fun backup() = TransferSnapshot(
        1, id(100), Instant.parse("2026-11-10T12:00:00.000Z"), TransferKind.BACKUP, TransferGroup.entries.toSet(),
        listOf(TransferCar(id(1), "Test café 🔧", null, Presence.Present(false), Presence.Present(84300),
            Presence.Present("TEST-VIN"), Presence.Present(null), Presence.Present(CarPreferences(true, false)),
            listOf(TransferTask(id(11), "Oil", 10000, 12, TaskIcon.DROPLET,
                Presence.Present(LastService(null, LocalDate(2026, 10, 1))), Presence.Present(2000))),
            Presence.Present(listOf(
                TransferHistory(id(101), id(11), "Oil", LocalDate(2026, 10, 1), 80000,
                    Presence.Present(HistoryDetails(null, "Test service", "Comment")),
                    Presence.Present(RollbackData(LastService(null, null)))),
                TransferHistory(id(102), id(99), "Deleted work", LocalDate(2026, 10, 1), 80000,
                    Presence.Present(HistoryDetails(0, null, null)), Presence.Present(null)),
            )))),
        Presence.Present(Settings.DEFAULT),
        Presence.Present(listOf(TransferReminder(id(11), Instant.parse("2026-11-10T11:00:00.000Z")))),
    )
}
