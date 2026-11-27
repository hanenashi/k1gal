package com.hanenashi.k1gal.util

import java.util.Locale

internal fun downloadProgressText(
    kind: String,
    currentFile: Int,
    fileCount: Int,
    downloadedBytes: Long,
    totalBytes: Long?,
): String {
    val progress = if (totalBytes != null) {
        "${formatMegabytes(downloadedBytes, includeUnit = false)}/${formatMegabytes(totalBytes)}"
    } else {
        formatMegabytes(downloadedBytes)
    }
    return "$kind $currentFile/$fileCount  $progress"
}

internal fun formatMegabytes(bytes: Long, includeUnit: Boolean = true): String {
    val value = String.format(Locale.US, "%.1f", bytes / 1_000_000.0)
    return if (includeUnit) "$value MB" else value
}
