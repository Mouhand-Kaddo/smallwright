package dev.smallwright.controller.ui

import java.util.Locale

/** How a download is drawn in the terminal: a bar with a percent, and sizes in megabytes. */
object DownloadProgress {
    private const val WIDTH = 20

    @JvmStatic
    fun bar(done: Long, total: Long): String {
        val percent = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)
        val filled = percent * WIDTH / 100
        return "[" +
            "█".repeat(filled) +
            "░".repeat(WIDTH - filled) +
            "] " +
            percent.toString().padStart(3) +
            "%"
    }

    @JvmStatic
    fun sizes(done: Long, total: Long): String =
        String.format(Locale.US, "%.1f of %.1f MB", done / 1e6, total / 1e6)
}
