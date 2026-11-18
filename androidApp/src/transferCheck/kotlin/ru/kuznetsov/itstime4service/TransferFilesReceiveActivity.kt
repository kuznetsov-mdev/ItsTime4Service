package ru.kuznetsov.itstime4service

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.security.MessageDigest

class TransferFilesReceiveActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri != null) contentResolver.openInputStream(uri)?.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var size = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                size += count
                check(size <= 16 * 1024 * 1024)
                digest.update(buffer, 0, count)
            }
            File(filesDir, "received-sha.txt").writeText(digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
        }
        setResult(RESULT_OK)
        finish()
    }
}
