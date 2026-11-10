package ru.kuznetsov.itstime4service.domain.transfer

sealed interface TransferOutcome<out T> {
    data class Success<T>(val value: T) : TransferOutcome<T>
    data class Failure(val reason: TransferFailure) : TransferOutcome<Nothing>
}

sealed interface TransferFailure {
    data object PermissionDenied : TransferFailure
    data object FileUnavailable : TransferFailure
    data object InsufficientSpace : TransferFailure
    data object ReadFailed : TransferFailure
    data object WriteFailed : TransferFailure
    data object InvalidEnvelope : TransferFailure
    data object UnsupportedVersion : TransferFailure
    data object UnsupportedProtection : TransferFailure
    data object LimitExceeded : TransferFailure
    data object AuthenticationFailed : TransferFailure
    data object InvalidSnapshot : TransferFailure
    data object SourceIdentityConflict : TransferFailure
    data object StalePlan : TransferFailure
    data object InvalidOperationReuse : TransferFailure
    data object DatabaseReadFailed : TransferFailure
    data object DatabaseWriteFailed : TransferFailure
}
