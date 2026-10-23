package ru.kuznetsov.itstime4service.ui.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart

suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> reloadable(retries: Flow<Unit>, failed: T, load: () -> Flow<T>): Flow<T> =
    retries
        .onStart { emit(Unit) }
        .flatMapLatest { load().catch { emit(failed) } }
