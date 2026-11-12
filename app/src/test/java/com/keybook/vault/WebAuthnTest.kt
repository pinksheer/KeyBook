package com.keybook.vault

import com.keybook.vault.data.ItemType
import com.keybook.vault.data.VaultItem
import com.keybook.vault.passkeys.AssertionOptions
import com.keybook.vault.passkeys.CreationOptions
import com.keybook.vault.passkeys.PasskeyProviderEngine
import com.keybook.vault.passkeys.WebAuthn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class WebAuthnTest {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    private val credentialId = WebAuthn.encodeBase64Url(ByteArray(32) { (it + 1).toByte() })
    private val publicKey = WebAuthn.encodeBase64Url(keyPair.public.encoded)
    private val privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
    private val item = VaultItem(
        type = ItemType.PASSKEY,
        title = "Example",
        username = "ada@example.com",
        rpId = "example.com",
        credentialId = credentialId,
        privateKey = privateKey,
        publicKey = publicKey,
        userHandle = "dXNlci1oYW5kbGU",
        passkeySignCount = 0,
    )

    @Test
    fun `attestation object is produced with no client data when hash is supplied`() {
        val options = CreationOptions(
            rpId = "example.com",
            rpName = "Example",
            userId = "dXNlci1oYW5kbGU",
            userName = "ada@example.com",
            userDisplayName = "Ada",
            challenge = "Y2hhbGxlbmdl",
        )
        val json = WebAuthn.buildAttestationResponse(
            options = options,
            credentialId = credentialId,
            publicKeySpki = publicKey,
            clientDataHash = ByteArray(32) { 7 },
            origin = "android:apk-key-hash:test",
            packageName = "com.test",
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val response = root["response"]!!.jsonObject
        assertEquals(credentialId, root["id"]!!.jsonPrimitive.content)
        assertNull(response["clientDataJSON"])
        assertEquals(-7, response["publicKeyAlgorithm"]!!.jsonPrimitive.content.toInt())
        val attestation = Base64.getUrlDecoder().decode(response["attestationObject"]!!.jsonPrimitive.content)
        assertTrue(attestation.isNotEmpty())
        // CBOR map(3) with the "none" format and authData field.
        assertEquals(0xA3.toByte(), attestation[0])
        val attestationText = attestation.toString(Charsets.ISO_8859_1)
        assertTrue(attestationText.contains("none"))
        assertTrue(attestationText.contains("authData"))
    }

    @Test
    fun `assertion signature verifies over authenticator data and client hash`() {
        val clientDataHash = ByteArray(32) { (it * 3).toByte() }
        val options = AssertionOptions("example.com", "Y2hhbGxlbmdl", setOf(credentialId))
        val json = WebAuthn.buildAssertionResponse(
            options = options,
            item = item,
            signCount = 1,
            origin = "android:apk-key-hash:test",
            packageName = "com.test",
            clientDataHash = clientDataHash,
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val response = root["response"]!!.jsonObject
        assertNull(response["clientDataJSON"])
        val authData = Base64.getUrlDecoder().decode(response["authenticatorData"]!!.jsonPrimitive.content)
        val signature = Base64.getUrlDecoder().decode(response["signature"]!!.jsonPrimitive.content)
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyPair.public)
            update(authData)
            update(clientDataHash)
        }
        assertTrue(verifier.verify(signature))
        // signCount is the last 4 bytes of authenticatorData.
        assertEquals(1L, authData.takeLast(4).fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xffL) })
    }

    @Test
    fun `assertion includes client data json when hash is missing`() {
        val options = AssertionOptions("example.com", "Y2hhbGxlbmdl", emptySet())
        val json = WebAuthn.buildAssertionResponse(
            options = options,
            item = item,
            signCount = 2,
            origin = "https://example.com",
            packageName = "com.test",
            clientDataHash = null,
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val response = root["response"]!!.jsonObject
        val clientDataJson = Base64.getUrlDecoder().decode(response["clientDataJSON"]!!.jsonPrimitive.content)
        val clientText = clientDataJson.toString(Charsets.UTF_8)
        assertTrue(clientText.contains("\"type\":\"webauthn.get\""))
        assertTrue(clientText.contains("https://example.com"))
        val authData = Base64.getUrlDecoder().decode(response["authenticatorData"]!!.jsonPrimitive.content)
        val signature = Base64.getUrlDecoder().decode(response["signature"]!!.jsonPrimitive.content)
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyPair.public)
            update(authData)
            update(WebAuthn.sha256(clientDataJson))
        }
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `credential filter honors allow list`() {
        val allowed = WebAuthn.matchesRequest(
            item,
            AssertionOptions("example.com", "challenge", setOf(credentialId)),
        )
        val rejected = WebAuthn.matchesRequest(
            item,
            AssertionOptions("example.com", "challenge", setOf("different")),
        )
        assertTrue(allowed)
        assertFalse(rejected)
    }

    @Test
    fun `provider accepts matching origin and domain rp ids`() {
        val origin = "android:apk-key-hash:abc"
        assertTrue(PasskeyProviderEngine.isRpIdAllowed(origin, origin))
        assertTrue(PasskeyProviderEngine.isRpIdAllowed("example.com", origin))
        assertTrue(PasskeyProviderEngine.isRpIdAllowed("example.com", "https://example.com"))
        assertTrue(PasskeyProviderEngine.isRpIdAllowed("example.com", "https://login.example.com"))
        assertFalse(PasskeyProviderEngine.isRpIdAllowed("android:apk-key-hash:other", origin))
    }
}
