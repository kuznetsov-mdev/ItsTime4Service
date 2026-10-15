package ru.kuznetsov.itstime4service.domain.model

/**
 * How urgent a task is.
 * Declared from least to most urgent, so the worse of two statuses is `maxOf(a, b)`.
 */
enum class TaskStatus { OK, SOON, DUE }
