package ru.kuznetsov.itstime4service.domain.transfer

data class ImportTaskMatchKey(val targetCarId: String, val incomingTaskId: String)

internal class ImportTaskMatcher(tasks: List<TransferTask>, private val kind: TransferKind) {
    private val byId = tasks.associateBy { it.id }
    private val byName = tasks.groupBy { it.name }

    fun previousTargets(ids: List<String>): List<TransferTask> = ids.mapNotNull(byId::get)

    fun candidates(incoming: TransferTask, previous: List<TransferTask>): List<TransferTask> {
        val stable = byId[incoming.id].takeIf { kind == TransferKind.BACKUP }
        return (previous + listOfNotNull(stable) + byName[incoming.name].orEmpty()).distinctBy { it.id }
    }
}
