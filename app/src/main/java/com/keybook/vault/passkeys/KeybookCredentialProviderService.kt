package com.keybook.vault.passkeys

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import com.keybook.vault.data.VaultRepository

class KeybookCredentialProviderService : CredentialProviderService() {
    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onError(CreateCredentialUnknownException("仅支持通行密钥"))
            return
        }
        try {
            callback.onResult(
                BeginCreateCredentialResponse.Builder()
                    .addCreateEntry(PasskeyProviderEngine.createEntry(this))
                    .build()
            )
        } catch (e: Exception) {
            callback.onError(CreateCredentialUnknownException(e.message ?: "无法创建通行密钥"))
        }
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        try {
            val repository = VaultRepository.get(this)
            if (!repository.isUnlocked()) {
                val response = BeginGetCredentialResponse.Builder()
                    .addAuthenticationAction(PasskeyProviderEngine.unlockAction(this))
                    .build()
                callback.onResult(response)
                return
            }
            callback.onResult(PasskeyProviderEngine.buildGetResponse(this, request))
        } catch (e: GetCredentialException) {
            callback.onError(e)
        } catch (e: Exception) {
            callback.onError(GetCredentialUnknownException(e.message ?: "无法读取通行密钥"))
        }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }
}
