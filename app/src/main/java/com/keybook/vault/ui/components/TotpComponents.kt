package com.keybook.vault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.keybook.vault.totp.Totp
import com.keybook.vault.totp.TotpConfig
import com.keybook.vault.totp.TotpState
import kotlinx.coroutines.delay

@Composable
fun rememberTotpState(
    secret: String,
    algorithm: String,
    digits: Int,
    period: Int,
): TotpState? {
    val config = remember(secret, algorithm, digits, period) {
        TotpConfig(secret, algorithm, digits, period)
    }
    val state by produceState(
        initialValue = Totp.generate(config),
        key1 = config,
    ) {
        while (true) {
            value = Totp.generate(config)
            delay(250)
        }
    }
    return state
}
