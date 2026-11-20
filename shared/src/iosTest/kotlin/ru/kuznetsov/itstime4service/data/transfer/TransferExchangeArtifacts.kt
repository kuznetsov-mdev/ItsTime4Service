package ru.kuznetsov.itstime4service.data.transfer

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

internal actual val exchangePlatform = "iOS"

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun writeExchangeArtifact(name: String, bytes: ByteArray) {
    val directory = NSTemporaryDirectory() + "it4s-transfer-exchange"
    check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null))
    val output = "$directory/$exchangePlatform-$name.it4s"
    bytes.usePinned { pinned ->
        check(NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()).writeToFile(output, false))
    }
    println("IT4S_EXCHANGE $exchangePlatform $output")
}
