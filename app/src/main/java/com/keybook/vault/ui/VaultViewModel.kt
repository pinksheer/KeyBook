package com.keybook.vault.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.keybook.vault.data.VaultData
import com.keybook.vault.data.VaultItem
import com.keybook.vault.data.VaultRepository
import com.keybook.vault.security.InvalidSecretException
import com.keybook.vault.security.VaultDamagedException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.crypto.Cipher

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository.get(application)

    val lockState = repository.lockState
    val vault = repository.vault
    val security = repository.security

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun message(text: String) {
        messageChannel.trySend(text)
    }

    suspend fun createVault(password: String, confirmation: String): Result<Unit> {
        if (password.length < 8) return Result.failure(IllegalArgumentException("主密码至少需要 8 位"))
        if (password != confirmation) return Result.failure(IllegalArgumentException("两次输入的密码不一致"))
        return withPasswords(password) { chars ->
            repository.createVault(chars)
        }
    }

    suspend fun unlock(password: String): Result<Unit> {
        if (password.isBlank()) return Result.failure(IllegalArgumentException("请输入主密码"))
        return withPasswords(password) { chars -> repository.unlock(chars) }
    }

    fun prepareBiometricUnlock(): Result<Cipher> = repository.prepareBiometricUnlock()

    suspend fun unlockWithBiometric(cipher: Cipher): Result<Unit> = repository.unlockWithBiometric(cipher)

    fun prepareBiometricEnrollment(): Result<Cipher> = repository.prepareBiometricEnrollment()

    suspend fun completeBiometricEnrollment(cipher: Cipher): Result<Unit> =
        repository.completeBiometricEnrollment(cipher)

    fun cancelBiometricEnrollment() = repository.cancelBiometricEnrollment()

    fun disableBiometrics() = repository.disableBiometrics()

    suspend fun upsert(item: VaultItem): Result<Unit> = repository.upsert(item)

    suspend fun delete(itemId: String): Result<Unit> = repository.delete(itemId)

    suspend fun setFavorite(itemId: String, favorite: Boolean): Result<Unit> =
        repository.setFavorite(itemId, favorite)

    suspend fun createBackup(password: String, confirmation: String): Result<ByteArray> {
        if (password.length < 8) return Result.failure(IllegalArgumentException("备份密码至少需要 8 位"))
        if (password != confirmation) return Result.failure(IllegalArgumentException("两次输入的密码不一致"))
        return withPasswords(password) { repository.createBackup(it) }
    }

    suspend fun inspectBackup(bytes: ByteArray, password: String): Result<VaultData> {
        if (password.isBlank()) return Result.failure(IllegalArgumentException("请输入备份密码"))
        return withPasswords(password) { repository.decryptBackup(bytes, it) }
    }

    suspend fun applyRestore(data: VaultData): Result<Unit> = repository.replaceVault(data)

    fun markBackupExported() = repository.markBackupExported()

    suspend fun changePassword(current: String, newPassword: String, confirmation: String): Result<Unit> {
        if (current.isBlank()) return Result.failure(IllegalArgumentException("请输入当前主密码"))
        if (newPassword.length < 8) return Result.failure(IllegalArgumentException("新密码至少需要 8 位"))
        if (newPassword != confirmation) return Result.failure(IllegalArgumentException("两次输入的新密码不一致"))
        val currentChars = current.toCharArray()
        val newChars = newPassword.toCharArray()
        return try {
            repository.changePassword(currentChars, newChars)
        } finally {
            currentChars.fill('\u0000')
            newChars.fill('\u0000')
        }
    }

    fun setAutoLockSeconds(seconds: Long) = repository.setAutoLockSeconds(seconds)

    fun beginExternalFlow() = repository.beginExternalFlow()

    fun endExternalFlow() = repository.endExternalFlow()

    fun lockNow() = repository.lock()

    fun messageFor(error: Throwable): String {
        var cause: Throwable? = error
        while (cause != null) {
            when (cause) {
                is InvalidSecretException -> return cause.message ?: "密码不正确"
                is VaultDamagedException -> return cause.message ?: "数据已损坏"
                is IllegalArgumentException -> return cause.message ?: "输入有误"
            }
            cause = cause.cause
        }
        val raw = error.message.orEmpty()
        return when {
            raw.contains("KeyPermanentlyInvalidated", ignoreCase = true) ->
                "指纹信息已变更，请使用主密码解锁"
            raw.contains("AEADBadTag", ignoreCase = true) -> "验证失败，请重试"
            raw.contains("UserNotAuthenticated", ignoreCase = true) -> "需要指纹验证"
            raw.isBlank() -> "操作失败，请重试"
            else -> raw
        }
    }

    private suspend fun <T> withPasswords(password: String, block: suspend (CharArray) -> T): T {
        val chars = password.toCharArray()
        return try {
            block(chars)
        } finally {
            chars.fill('\u0000')
        }
    }
}
