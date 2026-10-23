package ru.kuznetsov.itstime4service.features.licenses

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the licenses screen against `THIRD_PARTY_NOTICES.md`: the same libraries with the same links and licenses,
 * every license file is in the resources, and the full ISC and MIT texts in the app are the ones in the notices.
 * Reads the files from the module directory, where Gradle runs the host tests.
 */
class ThirdPartyNoticesTest {

    private val notices = File("../THIRD_PARTY_NOTICES.md").readText()
    private val resources = File("src/commonMain/composeResources")

    @Test
    fun screenListsTheLibrariesOfTheNotices() {
        val rows = Regex("""^\| \[([^\]]+)]\(([^)]+)\)[^|]*\| ([^|]+?) \|$""", RegexOption.MULTILINE)
            .findAll(notices)
            .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            .toList()

        assertEquals(rows, openSourceLibraries.map { Triple(it.name, it.url, it.license) })
    }

    @Test
    fun everyLicenseFileIsInTheResources() {
        openSourceLibraries.map { it.licenseFile }.distinct().forEach { path ->
            val file = File(resources, path)
            assertTrue(file.isFile && file.readText().isNotBlank(), "missing or empty $path")
        }
    }

    @Test
    fun fullLicenseTextsMatchTheNotices() {
        openSourceLibraries.filter { it.license != APACHE }.map { it.licenseFile }.distinct().forEach { path ->
            val text = File(resources, path).readText().words()
            assertTrue(text in notices.words(), "$path differs from THIRD_PARTY_NOTICES.md")
        }
        assertTrue(File(resources, APACHE_FILE).readText().words().startsWith("Apache License Version 2.0, January 2004"))
    }

    private fun String.words() = split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    private companion object {
        const val APACHE = "Apache License 2.0"
        const val APACHE_FILE = "files/licenses/apache-2.0.txt"
    }
}
