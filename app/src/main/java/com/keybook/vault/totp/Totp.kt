package com.keybook.vault.totp

import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TotpConfig(
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
)

data class TotpState(
    val code: String,
    val remainingMillis: Long,
    val periodMillis: Long,
    val counter: Long,
) {
    val progress: Float
        get() = if (periodMillis <= 0L) 0f else remainingMillis.toFloat() / periodMillis

    val remainingSeconds: Int
        get() = ((remainingMillis + 999) / 1000).toInt().coerceAtLeast(0)

    val spacedCode: String
        get() = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code
}

data class OtpAuthDetails(
    val title: String,
    val username: String,
    val config: TotpConfig,
)

object Totp {
    fun generate(config: TotpConfig, atMillis: Long = System.currentTimeMillis()): TotpState? {
        val secret = decodeBase32(config.secret) ?: return null
        if (secret.isEmpty()) return null
        val period = config.period.coerceIn(10, 120)
        val digits = config.digits.coerceIn(6, 8)
        val algorithm = normalizeAlgorithm(config.algorithm)
        val seconds = atMillis / 1000L
        val counter = seconds / period
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = try {
            val mac = Mac.getInstance("Hmac$algorithm")
            mac.init(SecretKeySpec(secret, "Hmac$algorithm"))
            mac.doFinal(counterBytes)
        } catch (e: Exception) {
            return null
        }
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulo = POWERS_OF_TEN[digits]
        val code = (binary.toLong() % modulo).toString().padStart(digits, '0')
        val elapsedInPeriod = atMillis % (period * 1000L)
        return TotpState(
            code = code,
            remainingMillis = period * 1000L - elapsedInPeriod,
            periodMillis = period * 1000L,
            counter = counter,
        )
    }

    fun isValidSecret(secret: String): Boolean {
        val decoded = decodeBase32(secret) ?: return false
        return decoded.size >= 5
    }

    fun parseOtpAuth(input: String): OtpAuthDetails? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("otpauth://", ignoreCase = true)) return null
        return try {
            val uri = URI(trimmed)
            if (!uri.host.equals("totp", ignoreCase = true)) return null
            val params = parseQuery(uri.rawQuery)
            val secret = params["secret"] ?: return null
            if (!isValidSecret(secret)) return null
            val label = URLDecoder.decode(uri.rawPath.orEmpty().trimStart('/'), "UTF-8")
            val issuerParam = params["issuer"]?.trim().orEmpty()
            val labelIssuer = if (label.contains(':')) label.substringBefore(':').trim() else ""
            val account = if (label.contains(':')) label.substringAfter(':').trim() else label.trim()
            val title = issuerParam.ifBlank { labelIssuer }.ifBlank { "验证码" }
            OtpAuthDetails(
                title = title,
                username = account,
                config = TotpConfig(
                    secret = secret,
                    algorithm = normalizeAlgorithm(params["algorithm"] ?: "SHA1"),
                    digits = params["digits"]?.toIntOrNull()?.coerceIn(6, 8) ?: 6,
                    period = params["period"]?.toIntOrNull()?.coerceIn(10, 120) ?: 30,
                ),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun normalizeAlgorithm(value: String): String {
        return when (value.uppercase(Locale.US).replace("-", "").replace("_", "")) {
            "SHA256" -> "SHA256"
            "SHA512" -> "SHA512"
            else -> "SHA1"
        }
    }

    fun decodeBase32(input: String): ByteArray? {
        val clean = input.uppercase(Locale.US)
            .replace(" ", "")
            .replace("-", "")
            .replace("=", "")
        if (clean.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0L
        var bits = 0
        for (char in clean) {
            val value = when (char) {
                in 'A'..'Z' -> char - 'A'
                in '2'..'7' -> char - '2' + 26
                else -> return null
            }
            buffer = (buffer shl 5) or value.toLong()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write(((buffer shr bits) and 0xff).toInt())
                buffer = if (bits == 0) 0L else buffer and ((1L shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            if (!part.contains('=')) return@mapNotNull null
            val key = URLDecoder.decode(part.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(part.substringAfter('='), "UTF-8")
            key to value
        }.toMap()
    }

    private val POWERS_OF_TEN = longArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000)
}
