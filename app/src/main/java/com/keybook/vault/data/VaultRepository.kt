package com.keybook.vault.data

import android.content.Context
import android.os.SystemClock
import com.keybook.vault.security.BiometricKeyStore
import com.keybook.vault.security.InvalidSecretException
import com.keybook.vault.security.KeyEnvelope
import com.keybook.vault.security.VaultCrypto
import com.keybook.vault.security.VaultFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.Cipher

enum class LockState {
    LOADING,
    SETUP,
    LOCKED,
    UNLOCKED,
}

data class SecurityState(
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Long = SettingsStore.DEFAULT_AUTO_LOCK,
    val lastBackupAt: Long = 0L,
)

private data class VaultSession(
    val volumeKey: ByteArray,
    val data: VaultData,
)

/**
 * Owns the unlocked vault for the lifetime of the process. The volume key and the
 * decrypted items never touch disk; locking drops both from memory.
 */
class VaultRepository(context: Context) {
    private val appContext = context.applicationContext
    private val files = VaultFileStore(appContext)
    private val biometrics = BiometricKeyStore(appContext)
    private val settings = SettingsStore(appContext)
    private val sessionLock = Any()

    private var session: VaultSession? = null
    private var backgroundedAt: Long = 0L
    private var externalFlowStartedAt: Long = 0L

    private val _lockState = MutableStateFlow(LockState.LOADING)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _vault = MutableStateFlow<VaultData?>(null)
    val vault: StateFlow<VaultData?> = _vault.asStateFlow()

    private val _security = MutableStateFlow(SecurityState())
    val security: StateFlow<SecurityState> = _security.asStateFlow()

    fun bootstrap() {
        _lockState.value = if (files.isInitialized()) LockState.LOCKED else LockState.SETUP
        refreshSecurity()
    }

    fun refreshSecurity() {
        _security.value = SecurityState(
            biometricAvailable = biometrics.canAuthenticate(),
            biometricEnabled = biometrics.isEnrolled(),
            autoLockSeconds = settings.autoLockSeconds,
            lastBackupAt = settings.lastBackupAt,
        )
    }

    fun isUnlocked(): Boolean = synchronized(sessionLock) { session != null }

    fun isInitialized(): Boolean = files.isInitialized()

    suspend fun updatePasskeySignCount(itemId: String, count: Int): Result<Unit> = mutate { current ->
        current.copy(
            items = current.items.map {
                if (it.id == itemId) it.copy(passkeySignCount = count) else it
            },
        )
    }

    suspend fun createVault(password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(password.size >= 8) { "主密码至少需要 8 位" }
            if (files.isInitialized()) {
                return@withContext Result.failure(IllegalStateException("保险箱已存在"))
            }
            val volumeKey = VaultCrypto.randomBytes(VaultCrypto.KEY_BYTES)
            val envelope = VaultCrypto.wrapVolumeKey(volumeKey, password)
            val initialData = VaultData()
            val payload = VaultCrypto.encryptVault(volumeKey, initialData)
            files.writeEnvelope(envelope)
            files.writeVaultPayload(payload)
            synchronized(sessionLock) {
                session = VaultSession(volumeKey, initialData)
            }
            _vault.value = initialData
            _lockState.value = LockState.UNLOCKED
            Result.success(Unit)
        } catch (e: Exception) {
            clearFilesIfPartiallyInitialized()
            Result.failure(e)
        }
    }

    suspend fun unlock(password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val envelope = files.readEnvelope()
            val volumeKey = VaultCrypto.unwrapVolumeKey(envelope, password)
            val payload = files.readVaultPayload()
            val data = VaultCrypto.decryptVault(volumeKey, payload)
            synchronized(sessionLock) {
                session = VaultSession(volumeKey, data)
            }
            _vault.value = data
            _lockState.value = LockState.UNLOCKED
            backgroundedAt = 0L
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun lock() {
        synchronized(sessionLock) {
            session?.volumeKey?.fill(0)
            session = null
        }
        _vault.value = null
        if (_lockState.value == LockState.UNLOCKED) {
            _lockState.value = LockState.LOCKED
        }
    }

    fun onEnterBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
        if (settings.autoLockSeconds == 0L && isUnlocked() && externalFlowStartedAt == 0L) {
            lock()
        }
    }

    fun onEnterForeground() {
        refreshSecurity()
        if (!isUnlocked()) return
        // A system document picker is an intentional, short-lived detour. Keep the
        // session alive so the chosen backup can actually be written or read.
        if (externalFlowStartedAt > 0L) {
            backgroundedAt = 0L
            return
        }
        val timeout = settings.autoLockSeconds * 1_000L
        if (timeout == 0L) {
            lock()
            return
        }
        if (backgroundedAt > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
            if (elapsed >= timeout) lock()
        }
        backgroundedAt = 0L
    }

    fun beginExternalFlow() {
        externalFlowStartedAt = SystemClock.elapsedRealtime()
        backgroundedAt = 0L
    }

    fun endExternalFlow() {
        externalFlowStartedAt = 0L
        backgroundedAt = 0L
    }

    fun snapshot(): VaultData? = synchronized(sessionLock) { session?.data }

    suspend fun upsert(item: VaultItem): Result<Unit> = mutate { current ->
        val now = System.currentTimeMillis()
        val normalized = item.copy(
            title = item.title.trim().ifBlank { "未命名" },
            username = item.username.trim(),
            url = item.url.trim(),
            updatedAt = now,
        )
        val existing = current.items.any { it.id == normalized.id }
        val items = if (existing) {
            current.items.map { if (it.id == normalized.id) normalized else it }
        } else {
            listOf(normalized) + current.items
        }
        current.copy(items = items, updatedAt = now)
    }

    suspend fun delete(itemId: String): Result<Unit> = mutate { current ->
        current.copy(
            items = current.items.filterNot { it.id == itemId },
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun setFavorite(itemId: String, favorite: Boolean): Result<Unit> = mutate { current ->
        current.copy(
            items = current.items.map {
                if (it.id == itemId) it.copy(favorite = favorite, updatedAt = System.currentTimeMillis()) else it
            },
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun replaceVault(imported: VaultData): Result<Unit> = mutate { _ ->
        val seen = mutableSetOf<String>()
        val cleaned = imported.items.map { item ->
            if (item.id.isBlank() || !seen.add(item.id)) {
                item.copy(id = UUID.randomUUID().toString())
            } else {
                item
            }
        }
        VaultData(version = 1, items = cleaned, updatedAt = System.currentTimeMillis())
    }

    suspend fun createBackup(password: CharArray): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val data = snapshot() ?: return@withContext Result.failure(IllegalStateException("请先解锁"))
            Result.success(VaultCrypto.encryptBackup(data, password))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decryptBackup(bytes: ByteArray, password: CharArray): Result<VaultData> =
        withContext(Dispatchers.IO) {
            try {
                val payload = VaultCrypto.decryptBackup(bytes, password)
                if (payload.format != 1) {
                    Result.failure(IllegalStateException("不支持的备份格式"))
                } else {
                    Result.success(payload.vault)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun markBackupExported() {
        settings.lastBackupAt = System.currentTimeMillis()
        refreshSecurity()
    }

    suspend fun verifyPassword(password: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val envelope = files.readEnvelope()
            val candidate = VaultCrypto.unwrapVolumeKey(envelope, password)
            val current = synchronized(sessionLock) { session?.volumeKey }
                ?: return@withContext false
            val matches = VaultCrypto.constantTimeEquals(candidate, current)
            candidate.fill(0)
            matches
        } catch (e: Exception) {
            false
        }
    }

    suspend fun changePassword(currentPassword: CharArray, newPassword: CharArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(newPassword.size >= 8) { "新密码至少需要 8 位" }
                val envelope = files.readEnvelope()
                val candidate = VaultCrypto.unwrapVolumeKey(envelope, currentPassword)
                val currentVolumeKey = synchronized(sessionLock) { session?.volumeKey }
                    ?: return@withContext Result.failure(IllegalStateException("请先解锁"))
                if (!VaultCrypto.constantTimeEquals(candidate, currentVolumeKey)) {
                    candidate.fill(0)
                    return@withContext Result.failure(InvalidSecretException("当前主密码不正确"))
                }
                val newEnvelope = VaultCrypto.wrapVolumeKey(currentVolumeKey, newPassword)
                files.writeEnvelope(newEnvelope)
                candidate.fill(0)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun setAutoLockSeconds(seconds: Long) {
        settings.autoLockSeconds = seconds
        refreshSecurity()
    }

    fun prepareBiometricEnrollment(): Result<Cipher> {
        if (!isUnlocked()) return Result.failure(IllegalStateException("请先解锁"))
        if (!biometrics.canAuthenticate()) {
            return Result.failure(IllegalStateException("请先在系统设置中录入指纹"))
        }
        return try {
            Result.success(biometrics.createEnrollmentCipher())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeBiometricEnrollment(cipher: Cipher): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val volumeKey = synchronized(sessionLock) { session?.volumeKey }
                ?: return@withContext Result.failure(IllegalStateException("请先解锁"))
            biometrics.completeEnrollment(cipher, volumeKey)
            refreshSecurity()
            Result.success(Unit)
        } catch (e: Exception) {
            if (biometrics.isInvalidatedError(e)) biometrics.handleKeyInvalidation()
            refreshSecurity()
            Result.failure(e)
        }
    }

    fun cancelBiometricEnrollment() {
        biometrics.removeEnrollment()
        refreshSecurity()
    }

    fun disableBiometrics() {
        biometrics.removeEnrollment()
        refreshSecurity()
    }

    fun prepareBiometricUnlock(): Result<Cipher> {
        if (!biometrics.isEnrolled()) {
            return Result.failure(IllegalStateException("未启用指纹解锁"))
        }
        return try {
            Result.success(biometrics.createUnlockCipher())
        } catch (e: Exception) {
            if (biometrics.isInvalidatedError(e)) {
                biometrics.handleKeyInvalidation()
                refreshSecurity()
                Result.failure(IllegalStateException("指纹信息已变更，请使用主密码解锁"))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun unlockWithBiometric(cipher: Cipher): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val volumeKey = biometrics.decryptVolumeKey(cipher)
            val payload = files.readVaultPayload()
            val data = VaultCrypto.decryptVault(volumeKey, payload)
            synchronized(sessionLock) {
                session = VaultSession(volumeKey, data)
            }
            _vault.value = data
            _lockState.value = LockState.UNLOCKED
            backgroundedAt = 0L
            Result.success(Unit)
        } catch (e: Exception) {
            if (biometrics.isInvalidatedError(e)) {
                biometrics.handleKeyInvalidation()
                refreshSecurity()
            }
            Result.failure(e)
        }
    }

    private suspend fun mutate(transform: (VaultData) -> VaultData): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                synchronized(sessionLock) {
                    val current = session ?: return@withContext Result.failure(
                        IllegalStateException("保险箱已锁定")
                    )
                    val updated = transform(current.data)
                    val payload = VaultCrypto.encryptVault(current.volumeKey, updated)
                    files.writeVaultPayload(payload)
                    session = current.copy(data = updated)
                    _vault.value = updated
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun clearFilesIfPartiallyInitialized() {
        if (!files.isInitialized()) {
            files.deleteAll()
        }
    }

    companion object {
        @Volatile
        private var instance: VaultRepository? = null

        fun get(context: Context): VaultRepository {
            return instance ?: synchronized(this) {
                instance ?: VaultRepository(context.applicationContext).also {
                    instance = it
                    it.bootstrap()
                }
            }
        }
    }
}
