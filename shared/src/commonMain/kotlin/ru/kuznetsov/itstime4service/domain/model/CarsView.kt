package ru.kuznetsov.itstime4service.domain.model

/**
 * How the "My cars" list is shown: [LIST] — compact rows; [TASKS] — cards with the upcoming tasks;
 * [TASKS_AND_HISTORY] — cards with the upcoming tasks and the latest history records.
 */
enum class CarsView { LIST, TASKS, TASKS_AND_HISTORY }
