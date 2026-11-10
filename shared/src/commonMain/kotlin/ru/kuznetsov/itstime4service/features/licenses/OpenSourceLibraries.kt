package ru.kuznetsov.itstime4service.features.licenses

/**
 * An open-source component the app ships with: its name, home page and the license it is used under.
 * [licenseFile] is the path of the full license text inside the compose resources.
 */
internal data class OpenSourceLibrary(
    val name: String,
    val url: String,
    val license: String,
    val licenseFile: String,
)

private const val APACHE_2_0 = "Apache License 2.0"
private const val APACHE_2_0_FILE = "files/licenses/apache-2.0.txt"

internal val openSourceLibraries = listOf(
    OpenSourceLibrary("Kotlin standard library", "https://github.com/JetBrains/kotlin", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("kotlinx-datetime", "https://github.com/Kotlin/kotlinx-datetime", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("kotlinx-io", "https://github.com/Kotlin/kotlinx-io", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("cryptography-kotlin", "https://github.com/whyoleg/cryptography-kotlin", APACHE_2_0, "files/licenses/cryptography-apache-2.0.txt"),
    OpenSourceLibrary("Bouncy Castle", "https://www.bouncycastle.org/", "Bouncy Castle Licence", "files/licenses/bouncy-castle.txt"),
    OpenSourceLibrary("OpenSSL", "https://github.com/openssl/openssl", APACHE_2_0, "files/licenses/openssl-apache-2.0.txt"),
    OpenSourceLibrary("Native Builds", "https://github.com/ensody/native-builds", APACHE_2_0, "files/licenses/native-builds-apache-2.0.txt"),
    OpenSourceLibrary(
        "Compose Multiplatform",
        "https://github.com/JetBrains/compose-multiplatform",
        APACHE_2_0,
        APACHE_2_0_FILE,
    ),
    OpenSourceLibrary("AndroidX", "https://github.com/androidx/androidx", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("Koin", "https://github.com/InsertKoinIO/koin", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("SQLDelight", "https://github.com/sqldelight/sqldelight", APACHE_2_0, APACHE_2_0_FILE),
    OpenSourceLibrary("Lucide", "https://github.com/lucide-icons/lucide", "ISC License", "files/licenses/lucide-isc.txt"),
    OpenSourceLibrary(
        "Compose Icons for Lucide",
        "https://github.com/composablehorizons/compose-icons",
        "MIT License",
        "files/licenses/compose-icons-mit.txt",
    ),
)
