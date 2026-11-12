package com.keybook.vault

import com.keybook.vault.totp.Totp
import com.keybook.vault.totp.TotpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TotpTest {
    private val sha1Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val sha256Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA===="
    private val sha512Secret =
        "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA="

    @Test
    fun `matches RFC 6238 SHA1 vectors`() {
        val vectors = mapOf(
            59L to "94287082",
            1_111_111_109L to "07081804",
            1_111_111_111L to "14050471",
            1_234_567_890L to "89005924",
            2_000_000_000L to "69279037",
        )
        vectors.forEach { (time, expected) ->
            val result = Totp.generate(
                TotpConfig(sha1Secret, "SHA1", 8, 30),
                atMillis = time * 1000L,
            )
            assertEquals(expected, result?.code)
        }
    }

    @Test
    fun `matches RFC 6238 SHA256 and SHA512 vectors`() {
        assertEquals(
            "46119246",
            Totp.generate(TotpConfig(sha256Secret, "SHA256", 8, 30), 59_000L)?.code,
        )
        assertEquals(
            "90693936",
            Totp.generate(TotpConfig(sha512Secret, "SHA512", 8, 30), 59_000L)?.code,
        )
    }

    @Test
    fun `decodes lowercase and spaced base32`() {
        val decoded = Totp.decodeBase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")
        assertNotNull(decoded)
        assertEquals("12345678901234567890", decoded!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `parses otpauth uri`() {
        val parsed = Totp.parseOtpAuth(
            "otpauth://totp/GitHub:ada%40example.com?secret=$sha1Secret&issuer=GitHub&algorithm=SHA256&digits=8&period=60"
        )
        assertNotNull(parsed)
        assertEquals("GitHub", parsed!!.title)
        assertEquals("ada@example.com", parsed.username)
        assertEquals("SHA256", parsed.config.algorithm)
        assertEquals(8, parsed.config.digits)
        assertEquals(60, parsed.config.period)
    }

    @Test
    fun `rejects invalid secret and non totp uri`() {
        assertNull(Totp.generate(TotpConfig("not base32!", "SHA1", 6, 30)))
        assertNull(Totp.parseOtpAuth("https://example.com"))
        assertNull(Totp.parseOtpAuth("otpauth://hotp/Test?secret=$sha1Secret"))
    }
}
