package com.keybook.vault.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ItemType {
    PASSWORD,
    TOTP,
    PASSKEY,
}

@Serializable
data class VaultItem(
    val id: String = UUID.randomUUID().toString(),
    val type: ItemType,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    @SerialName("totp_secret") val totpSecret: String = "",
    @SerialName("totp_algorithm") val totpAlgorithm: String = "SHA1",
    @SerialName("totp_digits") val totpDigits: Int = 6,
    @SerialName("totp_period") val totpPeriod: Int = 30,
    @SerialName("rp_id") val rpId: String = "",
    @SerialName("credential_id") val credentialId: String = "",
    @SerialName("private_key") val privateKey: String = "",
    @SerialName("public_key") val publicKey: String = "",
    @SerialName("user_handle") val userHandle: String = "",
    @SerialName("passkey_algorithm") val passkeyAlgorithm: String = "ES256",
    @SerialName("passkey_sign_count") val passkeySignCount: Int = 0,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val subtitle: String
        get() = when (type) {
            ItemType.PASSWORD -> username.ifBlank { url.ifBlank { "未填写账户" } }
            ItemType.TOTP -> username.ifBlank { "一次性验证码" }
            ItemType.PASSKEY -> if (rpId.isBlank()) "通行密钥" else rpId
        }

    val isSensitive: Boolean
        get() = when (type) {
            ItemType.PASSWORD -> password.isNotBlank()
            ItemType.TOTP -> totpSecret.isNotBlank()
            ItemType.PASSKEY -> privateKey.isNotBlank()
        }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return sequenceOf(
            title, username, url, notes, rpId, credentialId,
        ).any { it.contains(q, ignoreCase = true) }
    }
}

@Serializable
data class VaultData(
    val version: Int = 1,
    val items: List<VaultItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class BackupPayload(
    val format: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val vault: VaultData,
)

val ItemType.label: String
    get() = when (this) {
        ItemType.PASSWORD -> "密码"
        ItemType.TOTP -> "验证码"
        ItemType.PASSKEY -> "通行密钥"
    }
