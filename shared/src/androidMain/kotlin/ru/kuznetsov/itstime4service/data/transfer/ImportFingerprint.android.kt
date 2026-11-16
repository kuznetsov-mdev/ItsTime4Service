package ru.kuznetsov.itstime4service.data.transfer

import java.security.MessageDigest

internal actual fun transferSha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
