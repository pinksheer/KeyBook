package com.keybook.vault.security

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * On-disk formats. Files live in the app sandbox and are additionally encrypted.
 *
 * key.bin:   magic(8) | version(4) | iterations(4) | salt | nonce | wrapped volume key
 * vault.bin: magic(8) | version(4) | nonce | AES-GCM ciphertext
 */
class VaultFileStore(context: Context) {
    private val dir: File = context.filesDir
    private val keyFile = File(dir, "vault.key")
    private val vaultFile = File(dir, "vault.bin")

    fun isInitialized(): Boolean = keyFile.exists() && vaultFile.exists()

    fun hasKeyFile(): Boolean = keyFile.exists()

    fun readEnvelope(): KeyEnvelope {
        val bytes = readLimited(keyFile, MAX_KEY_FILE)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(KEY_MAGIC.size).also(input::readFully)
        if (!magic.contentEquals(KEY_MAGIC)) throw VaultDamagedException()
        val version = input.readInt()
        if (version != FORMAT_VERSION) throw VaultDamagedException("不支持的保险箱版本")
        val iterations = input.readInt()
        val salt = input.readSizedBytes(64)
        val nonce = input.readSizedBytes(32)
        val wrapped = input.readSizedBytes(256)
        if (salt.size < 8 || nonce.size != VaultCrypto.NONCE_BYTES) throw VaultDamagedException()
        if (iterations < 10_000) throw VaultDamagedException()
        return KeyEnvelope(iterations, salt, nonce, wrapped)
    }

    fun writeEnvelope(envelope: KeyEnvelope) {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(KEY_MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeInt(envelope.iterations)
                output.writeSizedBytes(envelope.salt)
                output.writeSizedBytes(envelope.nonce)
                output.writeSizedBytes(envelope.wrappedKey)
            }
            buffer.toByteArray()
        }
        writeAtomic(keyFile, bytes)
    }

    fun readVaultPayload(): EncryptedPayload {
        val bytes = readLimited(vaultFile, MAX_VAULT_FILE)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(VAULT_MAGIC.size).also(input::readFully)
        if (!magic.contentEquals(VAULT_MAGIC)) throw VaultDamagedException()
        val version = input.readInt()
        if (version != FORMAT_VERSION) throw VaultDamagedException("不支持的保险箱版本")
        val nonce = input.readSizedBytes(32)
        val ciphertext = input.readBytes()
        if (nonce.size != VaultCrypto.NONCE_BYTES || ciphertext.size < 16) throw VaultDamagedException()
        return EncryptedPayload(nonce, ciphertext)
    }

    fun writeVaultPayload(payload: EncryptedPayload) {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(VAULT_MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeSizedBytes(payload.nonce)
                output.write(payload.ciphertext)
            }
            buffer.toByteArray()
        }
        writeAtomic(vaultFile, bytes)
    }

    fun deleteAll() {
        keyFile.delete()
        vaultFile.delete()
    }

    private fun readLimited(file: File, max: Int): ByteArray {
        if (!file.exists()) throw VaultDamagedException("保险箱文件不存在")
        if (file.length() <= 0 || file.length() > max) throw VaultDamagedException()
        return FileInputStream(file).use { it.readBytes() }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    private fun DataInputStream.readSizedBytes(max: Int): ByteArray {
        val length = readInt()
        if (length < 0 || length > max) throw VaultDamagedException()
        return ByteArray(length).also(::readFully)
    }

    private fun DataOutputStream.writeSizedBytes(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val MAX_KEY_FILE = 64 * 1024
        private const val MAX_VAULT_FILE = 64 * 1024 * 1024
        private val KEY_MAGIC = "KBKEY001".toByteArray(Charsets.US_ASCII)
        private val VAULT_MAGIC = "KBVAULT1".toByteArray(Charsets.US_ASCII)
    }
}

object BackupFormat {
    private val MAGIC = "KBBACKUP".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val MAX_BACKUP = 64 * 1024 * 1024

    fun encode(salt: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(VaultCrypto.PBKDF2_ITERATIONS)
                output.writeInt(salt.size)
                output.write(salt)
                output.writeInt(nonce.size)
                output.write(nonce)
                output.write(ciphertext)
            }
            buffer.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): BackupContainer {
        if (bytes.size < 40 || bytes.size > MAX_BACKUP) throw VaultDamagedException()
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        if (!magic.contentEquals(MAGIC)) throw VaultDamagedException()
        val version = input.readInt()
        if (version != VERSION) throw VaultDamagedException("不支持的备份版本")
        val iterations = input.readInt()
        if (iterations != VaultCrypto.PBKDF2_ITERATIONS) throw VaultDamagedException("备份参数不匹配")
        val salt = ByteArray(input.readInt().also { if (it !in 8..64) throw VaultDamagedException() })
            .also(input::readFully)
        val nonce = ByteArray(input.readInt().also { if (it != VaultCrypto.NONCE_BYTES) throw VaultDamagedException() })
            .also(input::readFully)
        val ciphertext = input.readBytes()
        if (ciphertext.size < 16) throw VaultDamagedException()
        return BackupContainer(salt, nonce, ciphertext)
    }
}

data class BackupContainer(
    val salt: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)
