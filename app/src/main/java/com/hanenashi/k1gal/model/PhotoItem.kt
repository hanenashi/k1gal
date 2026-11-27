package com.hanenashi.k1gal.model

import java.io.File

data class PhotoItem(
    val dir: String,
    val jpg: String,
    val raw: String?,
    val thumbFile: File?,
    val viewFile: File?,
    val takenAt: String?,
    val av: String? = null,
    val tv: String? = null,
    val sv: String? = null,
    val xv: String? = null,
    val orientation: Int? = null,
    val loading: Boolean = false,
    val rawSizeBytes: Long? = null,
    val storageSlot: String = "1",
    val manualRotation: Int = 0,
    val thumbExifOrientation: Int? = null,
    val viewExifOrientation: Int? = null,
)

internal val PhotoItem.key: String get() = "$storageSlot/$dir/$jpg"
