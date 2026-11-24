package ru.kuznetsov.itstime4service.ui.transfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.service.chooser.ChooserResult
import androidx.core.content.IntentCompat

class TransferShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.takeIf { it.scheme == CALLBACK_SCHEME }?.schemeSpecificPart ?: return
        val result = if (Build.VERSION.SDK_INT >= 35) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOOSER_RESULT, ChooserResult::class.java)
        } else null
        val selected = if (result != null) {
            result.type == ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT && result.selectedComponent != null
        } else {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java) != null
        }
        if (selected) AndroidShareSignals.chosen(id)
    }

    internal companion object {
        private const val CALLBACK_SCHEME = "it4s-share"

        fun callbackIntent(context: Context, id: String): Intent =
            Intent(context, TransferShareReceiver::class.java)
                .setData(Uri.fromParts(CALLBACK_SCHEME, id, null))
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
}

internal object AndroidShareSignals {
    private val callbacks = mutableMapOf<String, () -> Unit>()
    fun register(id: String, callback: () -> Unit) { callbacks[id] = callback }
    fun chosen(id: String) { callbacks.remove(id)?.invoke() }
    fun remove(id: String) { callbacks.remove(id) }
}
