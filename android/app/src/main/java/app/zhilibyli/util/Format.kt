package app.zhilibyli.util

import java.util.Locale

/** 8,4 МБ / 931 КБ — как в списках Яндекс Музыки. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 МБ"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.0f КБ", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f МБ", mb)
    return String.format(Locale.getDefault(), "%.2f ГБ", mb / 1024.0)
}

/** 03:42 / 1:02:15 */
fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}

fun plural(count: Int, one: String, few: String, many: String): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}
