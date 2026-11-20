package ru.kuznetsov.itstime4service.data.transfer

import java.io.File

internal actual val exchangePlatform = "Android"

internal actual fun writeExchangeArtifact(name: String, bytes: ByteArray) {
    val directory = File(System.getProperty("java.io.tmpdir"), "it4s-transfer-exchange").also { it.mkdirs() }
    val output = File(directory, "$exchangePlatform-$name.it4s")
    output.writeBytes(bytes)
    println("IT4S_EXCHANGE $exchangePlatform ${output.absolutePath}")
}
