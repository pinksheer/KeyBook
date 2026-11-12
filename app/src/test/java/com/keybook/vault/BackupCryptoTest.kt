package com.keybook.vault

import com.keybook.vault.data.ItemType
import com.keybook.vault.data.VaultData
import com.keybook.vault.data.VaultItem
import com.keybook.vault.security.InvalidSecretException
import com.keybook.vault.security.VaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val vault = VaultData(
        items = listOf(
            VaultItem(
                type = ItemType.PASSWORD,
                title = "GitHub",
                username = "ada@example.com",
                password = "correct horse battery staple",
                url = "https://github.com",
                notes = "two factor enabled",
            ),
            VaultItem(
                type = ItemType.TOTP,
                title = "Example",
                username = "ada",
                totpSecret = "JBSWY3DPEHPK3PXP",
            ),
            VaultItem(
                type = ItemType.PASSKEY,
                title = "Passkey",
                rpId = "example.com",
                credentialId = "credential-id",
                privateKey = "private-key-material",
                publicKey = "public-key-material",
            ),
        ),
        updatedAt = 123L,
    )

    @Test
    fun `backup round trip preserves every field`() {
        val password = "backup-passphrase".toCharArray()
        val encrypted = VaultCrypto.encryptBackup(vault, password)
        assertTrue(encrypted.size > 64)
        val restored = VaultCrypto.decryptBackup(encrypted, password)
        assertEquals(1, restored.format)
        assertEquals(vault, restored.vault)
    }

    @Test
    fun `wrong password cannot open backup`() {
        val encrypted = VaultCrypto.encryptBackup(vault, "right-password".toCharArray())
        val failed = runCatching {
            VaultCrypto.decryptBackup(encrypted, "wrong-password".toCharArray())
        }
        assertTrue(failed.exceptionOrNull() is InvalidSecretException)
    }

    @Test
    fun `tampered backup is rejected`() {
        val encrypted = VaultCrypto.encryptBackup(vault, "right-password".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        val failed = runCatching {
            VaultCrypto.decryptBackup(encrypted, "right-password".toCharArray())
        }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `ciphertexts use unique nonces`() {
        val password = "backup-passphrase".toCharArray()
        val first = VaultCrypto.encryptBackup(vault, password)
        val second = VaultCrypto.encryptBackup(vault, password)
        assertNotEquals(first.toList(), second.toList())
    }
}
