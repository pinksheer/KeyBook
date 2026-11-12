package com.keybook.vault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

class BiometricAuthenticator(private val activity: FragmentActivity) {

    fun authenticate(
        title: String,
        subtitle: String,
        negativeText: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticatedCipher = result.cryptoObject?.cipher ?: cipher
                    onSuccess(authenticatedCipher)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        -> onCancel()
                        BiometricPrompt.ERROR_LOCKOUT ->
                            onError("尝试次数过多，请稍后再试或使用主密码")
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            onError("指纹验证已被系统锁定，请使用主密码")
                        BiometricPrompt.ERROR_NO_BIOMETRICS ->
                            onError("尚未录入指纹，请先在系统设置中添加")
                        BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                            onError("此设备不支持指纹识别")
                        BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                            onError("指纹传感器暂时不可用，请稍后再试")
                        else -> onError(errString.toString())
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeText)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

fun Activity.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    else -> baseContext.findFragmentActivity()
}
