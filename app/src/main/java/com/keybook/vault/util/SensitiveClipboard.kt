package com.keybook.vault.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SensitiveClipboard {
    private const val CLEAR_DELAY_MS = 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    fun copy(context: Context, value: String, label: String) {
        if (value.isBlank()) return
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_DELAY_MS)
            val current = manager.primaryClip
            val currentText = current?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            if (currentText == value) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.clearPrimaryClip()
                } else {
                    manager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
}
