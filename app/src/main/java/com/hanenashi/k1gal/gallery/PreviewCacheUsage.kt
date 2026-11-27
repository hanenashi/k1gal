package com.hanenashi.k1gal.gallery

internal data class PreviewCacheUsage(val bytes: Long, val files: Int)

internal val previewCacheLimitsMb = listOf(0, 25, 100, 250)
