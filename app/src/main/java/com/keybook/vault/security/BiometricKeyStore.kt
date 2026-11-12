package com.keybook.vault.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the random volume key with an Android Keystore AES key that can only be
 * used after a strong biometric authentication. The wrapped key is useless if
 * the keystore key is invalidated (for example after new fingerprints enroll).
 */
class BiometricKeyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun canAuthenticate(): Boolean {
        val result = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isEnrolled(): Boolean {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false
        if (!prefs.contains(KEY_CIPHERTEXT) || !prefs.contains(KEY_IV)) return false
        return try {
            keyStore().containsAlias(ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    /** Creates a fresh keystore key and returns an encrypt cipher awaiting biometric auth. */
    fun createEnrollmentCipher(): Cipher {
        val store = keyStore()
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        generator.init(builder.build())
        generator.generateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadKey())
        return cipher
    }

    fun completeEnrollment(cipher: Cipher, volumeKey: ByteArray) {
        val encrypted = cipher.doFinal(volumeKey)
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    /** Creates a decrypt cipher for the stored wrapped volume key. */
    fun createUnlockCipher(): Cipher {
        val encrypted = prefs.getString(KEY_CIPHERTEXT, null)
            ?: throw IllegalStateException("未启用指纹解锁")
        val iv = prefs.getString(KEY_IV, null)
            ?: throw IllegalStateException("指纹数据不完整")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            loadKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        // Keep ciphertext available to the caller without exposing it through prefs.
        enrollmentCiphertext = Base64.decode(encrypted, Base64.NO_WRAP)
        return cipher
    }

    fun decryptVolumeKey(cipher: Cipher): ByteArray {
        val encrypted = enrollmentCiphertext ?: throw IllegalStateException("缺少指纹密文")
        return try {
            cipher.doFinal(encrypted)
        } finally {
            enrollmentCiphertext = null
        }
    }

    fun removeEnrollment() {
        prefs.edit().clear().apply()
        enrollmentCiphertext = null
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    fun handleKeyInvalidation() {
        removeEnrollment()
    }

    fun isInvalidatedError(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is KeyPermanentlyInvalidatedException) return true
            cause = cause.cause
        }
        return false
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun loadKey(): SecretKey {
        val store = keyStore()
        val entry = store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("指纹密钥不存在")
        return entry.secretKey
    }

    private var enrollmentCiphertext: ByteArray? = null

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "keybook_biometric_volume_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREF_FILE = "keybook.biometric"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CIPHERTEXT = "wrapped_key"
        private const val KEY_IV = "iv"
    }
}
