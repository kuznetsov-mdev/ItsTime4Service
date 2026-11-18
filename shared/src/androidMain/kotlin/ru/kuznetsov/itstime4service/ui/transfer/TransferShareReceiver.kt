package ru.kuznetsov.itstime4service.ui.transfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Build

class TransferShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("operation") ?: return
        val chosen = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT) as? ComponentName)
        if (chosen != null) AndroidShareSignals.chosen(id)
    }
}

internal object AndroidShareSignals {
    private val callbacks = mutableMapOf<String, () -> Unit>()
    fun register(id: String, callback: () -> Unit) { callbacks[id] = callback }
    fun chosen(id: String) { callbacks.remove(id)?.invoke() }
    fun remove(id: String) { callbacks.remove(id) }
}
