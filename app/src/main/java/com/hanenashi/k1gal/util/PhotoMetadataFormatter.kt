package com.hanenashi.k1gal.util

import com.hanenashi.k1gal.model.PhotoItem

internal fun PhotoItem.cardMetaText(): String =
    compactDate(takenAt) ?: if (raw != null) "RAW" else "JPG"

private fun compactDate(value: String?): String? {
    if (value == null) return null
    Regex("^(\\d{4})[-:](\\d{2})[-:](\\d{2})").find(value)?.let { match ->
        return "${match.groupValues[1].takeLast(2)}/${match.groupValues[2]}/${match.groupValues[3]}"
    }
    return Regex("^(\\d{2})/(\\d{2})/(\\d{2})").find(value)?.value
}

internal fun PhotoItem.exposureMetaText(): String =
    listOfNotNull(
        av?.let { "f/$it" },
        tv?.let { formatShutter(it) },
        sv?.let { "ISO $it" },
        xv?.takeUnless { it == "0" || it == "0.0" }?.let { "EV $it" },
    ).joinToString("  ")

internal fun PhotoItem.viewerTimestamp(): String =
    takenAt?.replace('T', ' ') ?: dir

internal fun orientationRotation(orientation: Int?): Float = when (orientation) {
    3 -> 180f
    6 -> 90f
    8 -> 270f
    else -> 0f
}

internal fun PhotoItem.previewRotation(exifOrientation: Int?): Float {
    // Coil already applies orientation embedded in the local JPEG.
    val cameraRotation = if (exifOrientation in 2..8) 0f else orientationRotation(orientation)
    return (cameraRotation + manualRotation) % 360f
}

private fun formatShutter(value: String): String {
    val parts = value.split(".", limit = 2)
    if (parts.size != 2) return value
    val numerator = parts[0].toIntOrNull() ?: return value
    val denominator = parts[1].toIntOrNull() ?: return value
    if (denominator == 0) return value
    return when {
        numerator == 1 -> "1/$denominator"
        denominator == 1 -> "$numerator″"
        else -> "${numerator.toDouble() / denominator}″"
    }
}
