package com.keybook.vault.security

import com.keybook.vault.data.BackupPayload
import com.keybook.vault.data.VaultData
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class InvalidSecretException(message: String = "密码不正确") : Exception(message)
class VaultDamagedException(message: String = "保险箱数据已损坏") : Exception(message)

/**
 * All cryptographic primitives used by Keybook.
 *
 * Vault data is encrypted with a random 256-bit volume key using AES-256-GCM.
 * The volume key is wrapped by a PBKDF2-HMAC-SHA256 key derived from the master password.
 * Backups use the same construction with an independent salt and the backup password.
 */
object VaultCrypto {
    const val PBKDF2_ITERATIONS = 210_000
    const val KEY_BYTES = 32
    const val GCM_TAG_BITS = 128
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12

    private val secureRandom = SecureRandom()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return nonce to cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return cipher.doFinal(ciphertext)
    }

    fun wrapVolumeKey(volumeKey: ByteArray, password: CharArray): KeyEnvelope {
        val salt = randomBytes(SALT_BYTES)
        val derived = deriveKey(password, salt)
        val (nonce, wrapped) = encrypt(derived.encoded, volumeKey)
        return KeyEnvelope(PBKDF2_ITERATIONS, salt, nonce, wrapped)
    }

    fun unwrapVolumeKey(envelope: KeyEnvelope, password: CharArray): ByteArray {
        val derived = deriveKey(password, envelope.salt, envelope.iterations)
        return try {
            decrypt(derived.encoded, envelope.nonce, envelope.wrappedKey)
        } catch (e: Exception) {
            throw InvalidSecretException()
        }
    }

    fun encryptVault(volumeKey: ByteArray, vault: VaultData): EncryptedPayload {
        val plaintext = json.encodeToString(VaultData.serializer(), vault).toByteArray(StandardCharsets.UTF_8)
        val (nonce, ciphertext) = encrypt(volumeKey, plaintext)
        return EncryptedPayload(nonce, ciphertext)
    }

    fun decryptVault(volumeKey: ByteArray, payload: EncryptedPayload): VaultData {
        return try {
            val plaintext = decrypt(volumeKey, payload.nonce, payload.ciphertext)
            json.decodeFromString(VaultData.serializer(), plaintext.toString(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw VaultDamagedException()
        }
    }

    fun encryptBackup(vault: VaultData, password: CharArray): ByteArray {
        require(password.size >= 8) { "备份密码至少需要 8 位" }
        val payload = BackupPayload(vault = vault)
        val plaintext = json.encodeToString(BackupPayload.serializer(), payload)
            .toByteArray(StandardCharsets.UTF_8)
        val salt = randomBytes(SALT_BYTES)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val (nonce, ciphertext) = encrypt(key.encoded, plaintext)
        return BackupFormat.encode(salt, nonce, ciphertext)
    }

    fun decryptBackup(bytes: ByteArray, password: CharArray): BackupPayload {
        val container = try {
            BackupFormat.decode(bytes)
        } catch (e: Exception) {
            throw VaultDamagedException("不是有效的密码本备份文件")
        }
        val key = deriveKey(password, container.salt, PBKDF2_ITERATIONS)
        val plaintext = try {
            decrypt(key.encoded, container.nonce, container.ciphertext)
        } catch (e: Exception) {
            throw InvalidSecretException("备份密码不正确")
        }
        return try {
            json.decodeFromString(BackupPayload.serializer(), plaintext.toString(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw VaultDamagedException("备份内容无法读取")
        }
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun hmac(algorithm: String, key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("Hmac$algorithm")
        mac.init(SecretKeySpec(key, "Hmac$algorithm"))
        return mac.doFinal(message)
    }
}

data class KeyEnvelope(
    val iterations: Int,
    val salt: ByteArray,
    val nonce: ByteArray,
    val wrappedKey: ByteArray,
)

data class EncryptedPayload(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)
