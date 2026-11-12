package com.keybook.vault.security

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64

data class GeneratedPasskey(
    val credentialId: String,
    val privateKey: String,
    val publicKey: String,
    val userHandle: String,
)

/**
 * Generates the WebAuthn ES256 material stored inside a passkey entry.
 * Private keys are PKCS#8 and only exist inside the encrypted vault.
 */
object PasskeyFactory {
    private val random = SecureRandom()

    fun generate(
        rpId: String,
        username: String,
        userHandleOverride: String? = null,
    ): GeneratedPasskey {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = generator.generateKeyPair()
        val credentialId = ByteArray(32).also(random::nextBytes)
        val userHandle = userHandleOverride?.takeIf { it.isNotBlank() } ?: base64Url(
            MessageDigest.getInstance("SHA-256")
                .digest("$rpId|$username".toByteArray(Charsets.UTF_8))
                .copyOf(16)
        )
        return GeneratedPasskey(
            credentialId = base64Url(credentialId),
            privateKey = Base64.getEncoder().encodeToString(pair.private.encoded),
            publicKey = base64Url(pair.public.encoded),
            userHandle = userHandle,
        )
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
