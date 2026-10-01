package com.example.yuewen.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 把 epoch millis 转成「刚刚 / x分钟前 / x小时前 / x天前 / yyyy-MM-dd」。 */
fun formatRelativeTime(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val now = Instant.now().toEpochMilli()
    val diff = now - epochMillis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute}分钟前"
        diff < day -> "${diff / hour}小时前"
        diff < 7 * day -> "${diff / day}天前"
        else -> {
            val d = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            if (d.year == LocalDate.now().year) d.format(DateTimeFormatter.ofPattern("M月d日"))
            else d.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        }
    }
}
