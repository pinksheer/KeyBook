package com.keybook.vault.passkeys

import com.keybook.vault.data.VaultItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class PasskeyProtocolException(message: String) : Exception(message)

data class CreationOptions(
    val rpId: String,
    val rpName: String,
    val userId: String,
    val userName: String,
    val userDisplayName: String,
    val challenge: String,
)

data class AssertionOptions(
    val rpId: String,
    val challenge: String,
    val allowCredentialIds: Set<String>,
)

/**
 * Minimal WebAuthn authenticator implementation for the Android Credential Manager
 * passkey provider. Attestation uses the "none" format.
 *
 * The response/CBOR structure follows the Android identity-samples CredentialProvider
 * reference (Apache-2.0): https://github.com/android/identity-samples
 */
object WebAuthn {
    private val json = Json { ignoreUnknownKeys = true }
    private val b64UrlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val b64UrlDecoder = Base64.getUrlDecoder()

    fun parseCreationOptions(raw: String): CreationOptions {
        val root = parseObject(raw)
        val rp = root["rp"]?.jsonObject ?: throw PasskeyProtocolException("缺少 rp")
        val user = root["user"]?.jsonObject ?: throw PasskeyProtocolException("缺少 user")
        val rpId = rp["id"]?.jsonPrimitive?.content.orEmpty()
        if (rpId.isBlank()) throw PasskeyProtocolException("RP ID 不能为空")
        val challenge = root["challenge"]?.jsonPrimitive?.content
            ?: throw PasskeyProtocolException("缺少 challenge")
        val params = root["pubKeyCredParams"] as? JsonArray
        if (params != null) {
            val supported = params.any { parameter ->
                (parameter as? JsonObject)?.get("alg")?.jsonPrimitive?.content?.toIntOrNull() == -7
            }
            if (!supported) throw PasskeyProtocolException("仅支持 ES256 通行密钥")
        }
        return CreationOptions(
            rpId = rpId,
            rpName = rp["name"]?.jsonPrimitive?.content.orEmpty(),
            userId = user["id"]?.jsonPrimitive?.content.orEmpty(),
            userName = user["name"]?.jsonPrimitive?.content.orEmpty(),
            userDisplayName = user["displayName"]?.jsonPrimitive?.content ?: user["name"]?.jsonPrimitive?.content.orEmpty(),
            challenge = challenge,
        )
    }

    fun parseAssertionOptions(raw: String): AssertionOptions {
        val root = parseObject(raw)
        val rpId = root["rpId"]?.jsonPrimitive?.content.orEmpty()
        if (rpId.isBlank()) throw PasskeyProtocolException("RP ID 不能为空")
        val challenge = root["challenge"]?.jsonPrimitive?.content
            ?: throw PasskeyProtocolException("缺少 challenge")
        val allowed = mutableSetOf<String>()
        (root["allowCredentials"] as? JsonArray)?.forEach { element ->
            val id = (element as? JsonObject)?.get("id")?.jsonPrimitive?.content
            if (!id.isNullOrBlank()) allowed += id
        }
        return AssertionOptions(rpId, challenge, allowed)
    }

    fun buildAttestationResponse(
        options: CreationOptions,
        credentialId: String,
        publicKeySpki: String,
        clientDataHash: ByteArray?,
        origin: String,
        packageName: String,
    ): String {
        val credentialBytes = decodeBase64Url(credentialId)
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(decodeBase64Url(publicKeySpki))) as ECPublicKey
        val coseKey = coseKey(publicKey)
        val authData = authenticatorData(
            rpId = options.rpId,
            flags = 0x45, // UP | UV | AT
            signCount = 0,
            attestedCredential = AttestedCredential(credentialBytes, coseKey),
        )
        val attestationObject = Cbor.encode(
            mapOf(
                "fmt" to "none",
                "attStmt" to emptyMap<String, Any>(),
                "authData" to authData,
            )
        )
        val clientDataJson = if (clientDataHash == null) {
            buildClientDataJson("webauthn.create", options.challenge, origin, packageName)
        } else {
            null
        }
        return buildJsonObject {
            put("id", credentialId)
            put("rawId", credentialId)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            putJsonObject("response") {
                if (clientDataJson != null) put("clientDataJSON", base64Url(clientDataJson))
                put("attestationObject", base64Url(attestationObject))
                putJsonArray("transports") { add(kotlinx.serialization.json.JsonPrimitive("internal")) }
                put("publicKeyAlgorithm", -7)
                put("publicKey", publicKeySpki)
            }
            putJsonObject("clientExtensionResults") {
                putJsonObject("credProps") { put("rk", true) }
            }
        }.toString()
    }

    fun buildAssertionResponse(
        options: AssertionOptions,
        item: VaultItem,
        signCount: Int,
        origin: String,
        packageName: String,
        clientDataHash: ByteArray?,
    ): String {
        val credentialId = item.credentialId
        val authData = authenticatorData(
            rpId = options.rpId,
            flags = 0x05, // UP | UV
            signCount = signCount,
            attestedCredential = null,
        )
        val clientDataJson = if (clientDataHash == null) {
            buildClientDataJson("webauthn.get", options.challenge, origin, packageName)
        } else {
            null
        }
        val hash = clientDataHash ?: sha256(clientDataJson!!)
        val dataToSign = authData + hash
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(
            PKCS8EncodedKeySpec(Base64.getDecoder().decode(item.privateKey))
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(dataToSign)
            sign()
        }
        return buildJsonObject {
            put("id", credentialId)
            put("rawId", credentialId)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            putJsonObject("response") {
                if (clientDataJson != null) put("clientDataJSON", base64Url(clientDataJson))
                put("authenticatorData", base64Url(authData))
                put("signature", base64Url(signature))
                if (item.userHandle.isNotBlank()) put("userHandle", item.userHandle)
            }
            putJsonObject("clientExtensionResults") {}
        }.toString()
    }

    fun matchesRequest(item: VaultItem, options: AssertionOptions): Boolean {
        if (item.credentialId.isBlank() || item.privateKey.isBlank()) return false
        if (options.allowCredentialIds.isNotEmpty() && item.credentialId !in options.allowCredentialIds) return false
        return true
    }

    fun encodeBase64Url(bytes: ByteArray): String = b64UrlEncoder.encodeToString(bytes)

    fun decodeBase64Url(value: String): ByteArray = b64UrlDecoder.decode(value)

    fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private fun parseObject(raw: String): JsonObject {
        return try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            throw PasskeyProtocolException("无法解析通行密钥请求")
        }
    }

    private fun buildClientDataJson(type: String, challenge: String, origin: String, packageName: String): ByteArray {
        return buildJsonObject {
            put("type", type)
            put("challenge", challenge)
            put("origin", origin)
            put("androidPackageName", packageName)
        }.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private data class AttestedCredential(
        val credentialId: ByteArray,
        val coseKey: ByteArray,
    )

    private fun authenticatorData(
        rpId: String,
        flags: Int,
        signCount: Int,
        attestedCredential: AttestedCredential?,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(sha256(rpId.toByteArray(StandardCharsets.UTF_8)))
        out.write(flags)
        out.write(ByteBuffer.allocate(4).putInt(signCount).array())
        if (attestedCredential != null) {
            out.write(AAGUID)
            out.write(byteArrayOf((attestedCredential.credentialId.size shr 8).toByte(), attestedCredential.credentialId.size.toByte()))
            out.write(attestedCredential.credentialId)
            out.write(attestedCredential.coseKey)
        }
        return out.toByteArray()
    }

    private fun coseKey(publicKey: ECPublicKey): ByteArray {
        val x = fixed32(publicKey.w.affineX.toByteArray())
        val y = fixed32(publicKey.w.affineY.toByteArray())
        return Cbor.encode(
            mapOf(
                1 to 2, // EC2
                3 to -7, // ES256
                -1 to 1, // P-256
                -2 to x,
                -3 to y,
            )
        )
    }

    private fun fixed32(value: ByteArray): ByteArray {
        val start = if (value.size > 32 && value[0] == 0.toByte()) 1 else 0
        val length = value.size - start
        require(length <= 32) { "EC coordinate is too large" }
        val out = ByteArray(32)
        System.arraycopy(value, start, out, 32 - length, length)
        return out
    }

    private fun base64Url(bytes: ByteArray): String = b64UrlEncoder.encodeToString(bytes)

    private val AAGUID = "KeybookPasskey01".toByteArray(StandardCharsets.US_ASCII)

    /** Tiny canonical-enough CBOR encoder for WebAuthn structures. */
    private object Cbor {
        private const val UNSIGNED = 0x00
        private const val NEGATIVE = 0x01
        private const val BYTES = 0x02
        private const val TEXT = 0x03
        private const val ARRAY = 0x04
        private const val MAP = 0x05

        fun encode(value: Any?): ByteArray {
            return when (value) {
                is Int -> integer(value.toLong())
                is Long -> integer(value)
                is ByteArray -> header(BYTES, value.size.toLong()) + value
                is String -> {
                    val bytes = value.toByteArray(StandardCharsets.UTF_8)
                    header(TEXT, bytes.size.toLong()) + bytes
                }
                is List<*> -> {
                    var out = header(ARRAY, value.size.toLong())
                    value.forEach { out += encode(it) }
                    out
                }
                is Map<*, *> -> {
                    var out = header(MAP, value.size.toLong())
                    value.forEach { (k, v) ->
                        out += encode(k)
                        out += encode(v)
                    }
                    out
                }
                else -> throw PasskeyProtocolException("无法编码 CBOR 值")
            }
        }

        private fun integer(value: Long): ByteArray {
            return if (value >= 0) header(UNSIGNED, value) else header(NEGATIVE, -1 - value)
        }

        private fun header(major: Int, value: Long): ByteArray {
            val prefix = major shl 5
            return when {
                value < 24 -> byteArrayOf((prefix or value.toInt()).toByte())
                value <= 0xFF -> byteArrayOf((prefix or 24).toByte(), value.toByte())
                value <= 0xFFFF -> byteArrayOf(
                    (prefix or 25).toByte(),
                    (value shr 8).toByte(),
                    value.toByte(),
                )
                value <= 0xFFFFFFFFL -> byteArrayOf(
                    (prefix or 26).toByte(),
                    (value shr 24).toByte(),
                    (value shr 16).toByte(),
                    (value shr 8).toByte(),
                    value.toByte(),
                )
                else -> throw PasskeyProtocolException("CBOR 数值过大")
            }
        }
    }
}
