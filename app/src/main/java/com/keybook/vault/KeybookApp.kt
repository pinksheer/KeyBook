package com.keybook.vault

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.keybook.vault.data.VaultRepository

class KeybookApp : Application(), DefaultLifecycleObserver {
    lateinit var repository: VaultRepository
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        repository = VaultRepository.get(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        repository.onEnterForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        repository.onEnterBackground()
    }
}

