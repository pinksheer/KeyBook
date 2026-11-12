package com.keybook.vault.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("keybook.settings", Context.MODE_PRIVATE)

    var autoLockSeconds: Long
        get() = prefs.getLong(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK)
        set(value) = prefs.edit().putLong(KEY_AUTO_LOCK, value).apply()

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    companion object {
        const val DEFAULT_AUTO_LOCK = 60L
        private const val KEY_AUTO_LOCK = "auto_lock_seconds"
        private const val KEY_LAST_BACKUP = "last_backup_at"
    }
}
