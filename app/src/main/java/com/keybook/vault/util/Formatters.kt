package com.keybook.vault.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {
    private val dateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)

    fun date(epochMillis: Long): String = if (epochMillis <= 0) "—" else dateFormat.format(Date(epochMillis))

    fun dateTime(epochMillis: Long): String =
        if (epochMillis <= 0) "—" else dateTimeFormat.format(Date(epochMillis))

    fun relative(epochMillis: Long): String {
        if (epochMillis <= 0) return "从未"
        val diff = System.currentTimeMillis() - epochMillis
        if (diff < 0) return dateTime(epochMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes} 分钟前"
            hours < 24 -> "${hours} 小时前"
            days < 7 -> "${days} 天前"
            else -> date(epochMillis)
        }
    }

    fun backupFilename(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
        return "keybook-backup-$stamp.keybook"
    }
}
