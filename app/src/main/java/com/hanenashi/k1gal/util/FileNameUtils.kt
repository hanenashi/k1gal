package com.hanenashi.k1gal.util

import com.hanenashi.k1gal.model.PhotoItem

internal fun numberNearExtension(fileName: String): Int {
    val match = Regex("(\\d{4})(?=\\.[A-Za-z0-9]+$)").find(fileName)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
}

internal fun String.isRawName(): Boolean =
    endsWith(".PEF", ignoreCase = true) || endsWith(".DNG", ignoreCase = true) || endsWith(".RAW", ignoreCase = true)

internal fun PhotoItem.displayName(): String = jpg.substringBeforeLast(".")
