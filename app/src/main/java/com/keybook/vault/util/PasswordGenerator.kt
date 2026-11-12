package com.keybook.vault.util

import java.security.SecureRandom

data class PasswordOptions(
    val length: Int = 18,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
)

object PasswordGenerator {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val LOWER_AMBIGUOUS = "l"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val UPPER_AMBIGUOUS = "IO"
    private const val DIGITS = "23456789"
    private const val DIGITS_AMBIGUOUS = "01"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?"
    private val random = SecureRandom()

    fun generate(options: PasswordOptions): String {
        val pools = mutableListOf<String>()
        if (options.lowercase) pools += LOWER + if (options.excludeAmbiguous) "" else LOWER_AMBIGUOUS
        if (options.uppercase) pools += UPPER + if (options.excludeAmbiguous) "" else UPPER_AMBIGUOUS
        if (options.digits) pools += DIGITS + if (options.excludeAmbiguous) "" else DIGITS_AMBIGUOUS
        if (options.symbols) pools += SYMBOLS
        if (pools.isEmpty()) pools += LOWER

        val length = options.length.coerceIn(maxOf(8, pools.size), 64)
        val all = pools.joinToString("")
        val chars = CharArray(length)
        // Guarantee at least one character from each selected pool.
        pools.forEachIndexed { index, pool ->
            chars[index] = pool[random.nextInt(pool.length)]
        }
        for (i in pools.size until length) {
            chars[i] = all[random.nextInt(all.length)]
        }
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val temp = chars[i]
            chars[i] = chars[j]
            chars[j] = temp
        }
        return String(chars)
    }

    fun strength(password: String): Int {
        if (password.isEmpty()) return 0
        var score = 0
        if (password.length >= 10) score++
        if (password.length >= 16) score++
        if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score.coerceIn(0, 4)
    }

    fun strengthLabel(score: Int): String = when (score) {
        0, 1 -> "较弱"
        2 -> "一般"
        3 -> "较强"
        else -> "很强"
    }
}
