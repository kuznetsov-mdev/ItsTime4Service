package ru.kuznetsov.itstime4service.domain.model

/** Explicit urgency independent of enum declaration order. */
enum class TaskStatus(val priority: Int) {
    OK(3), SOON(2), DUE(0), RECOMMENDED(1),
}
