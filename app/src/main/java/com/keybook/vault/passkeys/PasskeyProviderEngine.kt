package com.keybook.vault.passkeys

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.keybook.vault.R
import com.keybook.vault.data.ItemType
import com.keybook.vault.data.VaultRepository
import java.security.MessageDigest
import java.util.Base64

object PasskeyProviderEngine {
    const val ACTION_CREATE = "com.keybook.vault.action.CREATE_PASSKEY"
    const val ACTION_GET = "com.keybook.vault.action.GET_PASSKEY"
    const val ACTION_UNLOCK = "com.keybook.vault.action.UNLOCK_PASSKEYS"
    const val EXTRA_CREDENTIAL_ID = "credential_id"
    const val EXTRA_RP_ID = "rp_id"

    fun createEntry(context: Context): CreateEntry {
        return CreateEntry.Builder(
            context.getString(R.string.app_name) + "通行密钥",
            pendingIntent(context, ACTION_CREATE, 2401),
        )
            .setDescription("保存到本机加密保险箱")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setPublicKeyCredentialCount(1)
            .build()
    }

    fun unlockAction(context: Context): AuthenticationAction {
        return AuthenticationAction(
            "解锁密码本",
            pendingIntent(context, ACTION_UNLOCK, 2402),
        )
    }

    fun buildGetResponse(
        context: Context,
        request: BeginGetCredentialRequest,
    ): BeginGetCredentialResponse {
        val repository = VaultRepository.get(context)
        val vault = repository.snapshot()
        if (vault == null) throw GetCredentialUnknownException("密码本已锁定")
        val entries = mutableListOf<PublicKeyCredentialEntry>()
        val seen = mutableSetOf<String>()

        request.beginGetCredentialOptions.forEach { option ->
            if (option !is BeginGetPublicKeyCredentialOption) return@forEach
            val options = runCatching { WebAuthn.parseAssertionOptions(option.requestJson) }.getOrNull()
                ?: return@forEach
            vault.items
                .filter { it.type == ItemType.PASSKEY }
                .filter { it.rpId == options.rpId }
                .filter { WebAuthn.matchesRequest(it, options) }
                .filter { seen.add(it.credentialId) }
                .forEach { item ->
                    val extras = Bundle().apply {
                        putString(EXTRA_CREDENTIAL_ID, item.credentialId)
                        putString(EXTRA_RP_ID, item.rpId)
                    }
                    val entry = PublicKeyCredentialEntry.Builder(
                        context,
                        item.username.ifBlank { item.title },
                        pendingIntent(
                            context,
                            ACTION_GET,
                            item.credentialId.hashCode(),
                            extras,
                        ),
                        option,
                    )
                        .setDisplayName(item.title.ifBlank { item.rpId })
                        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                        .build()
                    entries += entry
                }
        }

        if (entries.isEmpty()) {
            throw GetCredentialUnknownException("没有匹配的通行密钥")
        }
        return BeginGetCredentialResponse.Builder()
            .setCredentialEntries(entries)
            .build()
    }

    fun appOrigin(info: CallingAppInfo): String {
        val signer = info.signingInfo.apkContentsSigners.firstOrNull()
        if (signer == null) return "android:apk-key-hash:"
        val hash = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
        return "android:apk-key-hash:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    fun isRpIdAllowed(rpId: String, origin: String): Boolean {
        if (rpId.isBlank()) return false
        if (rpId == origin) return true
        if (rpId.startsWith("android:apk-key-hash:")) return false
        if (origin.startsWith("https://")) {
            val host = origin.removePrefix("https://").substringBefore('/')
            return rpId == host || host.endsWith(".$rpId")
        }
        // Native apps commonly use a domain RP ID and verify it through Digital Asset Links.
        // Credential Manager only returns passkeys for the RP ID the caller asked for.
        return rpId.contains('.')
    }

    private fun pendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        extras: Bundle? = null,
    ): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        if (extras != null) intent.putExtras(extras)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
