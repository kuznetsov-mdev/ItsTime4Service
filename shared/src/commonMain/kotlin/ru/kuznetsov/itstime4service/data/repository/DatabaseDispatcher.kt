package ru.kuznetsov.itstime4service.data.repository

import kotlinx.coroutines.Dispatchers

internal val databaseDispatcher = Dispatchers.Default.limitedParallelism(1)
