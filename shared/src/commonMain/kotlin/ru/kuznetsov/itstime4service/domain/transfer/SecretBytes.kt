package ru.kuznetsov.itstime4service.domain.transfer

class SecretBytes private constructor(private var bytes: ByteArray?) : AutoCloseable {
    internal fun copyBytes(): ByteArray = checkNotNull(bytes) { "Secret is closed" }.copyOf()

    override fun close() {
        bytes?.fill(0)
        bytes = null
    }

    override fun toString(): String = "SecretBytes"

    companion object {
        fun fromPassword(password: String): SecretBytes {
            require(password.isNotEmpty() && password.length <= TransferLimits.MAX_PASSWORD_BYTES) { "Invalid password length" }
            val bytes = try {
                password.encodeToByteArray(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                throw IllegalArgumentException("Invalid password Unicode")
            }
            if (bytes.size > TransferLimits.MAX_PASSWORD_BYTES) {
                bytes.fill(0)
                throw IllegalArgumentException("Invalid password length")
            }
            return SecretBytes(bytes)
        }
    }
}
