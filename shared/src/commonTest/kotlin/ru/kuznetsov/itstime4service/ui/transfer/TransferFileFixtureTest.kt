package ru.kuznetsov.itstime4service.ui.transfer

import kotlinx.coroutines.test.runTest
import ru.kuznetsov.itstime4service.domain.transfer.*
import ru.kuznetsov.itstime4service.data.transfer.TransferCodecImpl
import kotlin.test.*

internal object TransferFileCheckFixtures {
    fun plain(): ByteArray = decode("49543453010000000002000001357b7d7b22666f726d617456657273696f6e223a312c22736e617073686f744964223a2230303030303030302d303030302d343030302d383030302d303030303030303030363030222c22637265617465644174223a22323032362d31312d31375430393a30303a30302e3030305a222c226b696e64223a22636172222c22696e636c7564656447726f757073223a5b5d2c2263617273223a5b7b226964223a226331222c226272616e64223a2253796e74686574696320636172222c226d61696e74656e616e63654465736372697074696f6e223a6e756c6c2c227461736b73223a5b7b226964223a227431222c226e616d65223a224f696c222c22696e74657276616c4b6d223a31303030302c22696e74657276616c4d6f6e746873223a31322c2269636f6e223a2264726f706c6574227d5d7d5d7d")
    fun protected(): ByteArray = decode("49543453010100000092000001457b22636970686572223a224145532d3235362d47434d222c22697465726174696f6e73223a3630303030302c226b6466223a2250424b4446322d484d41432d534841323536222c226e6f6e6365223a2241414543417751464267634943516f4c222c2273616c74223a2241414543417751464267634943516f4c4441304f44773d3d222c227461674279746573223a31367db028436412db53b2ba2dd0a2fac9512ad41b016774327e64488c137c7b204a8d0efc7da27fa6d0cb55c9583fc17cc2adc086be51109194f8b81451490ca62cd4751322173288869002ef9ed82cd21ba8602f90699291a73e1609979d56d3e849d147ac605e15244a19f1d5c6543222bf7bcc7fffd35031be5e9c576a7fbdd458a4a365526628cafc2d86e7d8f1990eb01ab05d0b728db8f1ea12e2918f26e78243fd9231837dbddcc2d2123f76e3019a7cd4487dd83f1562d87b3560ed9cc5314e6150aac414bd90afa8e7d6bf3893df5ac2a28a5fd1a4e1037ff1cfe7217efa96f42b672301fa626dbcbc88689fbc99d5eb709e57afcc9474e7979b6dc328c4830e6071af9e23da8ee81967a2556d0ff5e25cbfe1df8f2566aec1874b09a17d78ff82dfcc14982d72d56ee7e5bb01ddcf6ee3ad616d6734ea26b451dc107a6068f6a21d1d")
    private fun decode(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

internal expect fun transferFileCheckCipher(): TransferCipher

class TransferFileFixtureTest {
    @Test
    fun independentProtectedFixtureAuthenticatesAndDecodesOnBothProviders() = runTest {
        val secret = SecretBytes.fromPassword("Synthetic-file-check")
        try {
            val opened = assertIs<TransferOutcome.Success<ByteArray>>(transferFileCheckCipher().open(TransferFileCheckFixtures.protected(), secret)).value
            assertContentEquals(TransferFileCheckFixtures.plain().copyOfRange(16, TransferFileCheckFixtures.plain().size), opened)
            val snapshot = assertIs<TransferOutcome.Success<TransferSnapshot>>(TransferCodecImpl().decode(opened)).value
            assertEquals("Synthetic car", snapshot.cars.single().brand)
        } finally { secret.close() }
    }
}
