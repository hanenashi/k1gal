package com.hanenashi.k1gal.gallery

import android.media.ExifInterface
import android.content.SharedPreferences
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.model.key
import java.io.File

internal class PreviewCache(private val cacheDir: File, private val orientations: SharedPreferences) {
    fun thumbnailFile(sd: String, dir: String, jpg: String): File =
        cachedPhotoFile(sd, dir, jpg, "thumbs")

    fun viewFile(sd: String, dir: String, jpg: String): File =
        cachedPhotoFile(sd, dir, jpg, "views")

    fun restore(photo: PhotoItem): PhotoItem {
        val sd = photo.storageSlot
        val thumb = thumbnailFile(sd, photo.dir, photo.jpg).takeIf { it.isFile && it.length() > 0L }
        val view = viewFile(sd, photo.dir, photo.jpg).takeIf { it.isFile && it.length() > 0L }
        return restoreOrientation(photo.copy(thumbFile = thumb, viewFile = view, loading = false))
    }

    private fun restoreOrientation(photo: PhotoItem): PhotoItem = photo.copy(
        orientation = photo.orientation ?: orientations.getInt("auto/${photo.key}", 0).takeIf { it in 1..8 },
        manualRotation = orientations.getInt("manual/${photo.key}", 0),
        thumbExifOrientation = photo.thumbFile?.exifOrientation(),
        viewExifOrientation = photo.viewFile?.exifOrientation(),
    )

    fun rememberOrientation(photo: PhotoItem) {
        photo.orientation?.takeIf { it in 1..8 }?.let {
            orientations.edit().putInt("auto/${photo.key}", it).apply()
        }
    }

    fun rotate(photo: PhotoItem, delta: Int?): PhotoItem {
        val angle = delta?.let { Math.floorMod(photo.manualRotation + it, 360) } ?: 0
        val editor = orientations.edit()
        if (angle == 0) editor.remove("manual/${photo.key}") else editor.putInt("manual/${photo.key}", angle)
        editor.apply()
        return photo.copy(manualRotation = angle)
    }

    fun loadExisting(): List<PhotoItem> =
        File(cacheDir, "photos/views").walkTopDown()
            .filter { it.isFile && it.extension.equals("JPG", ignoreCase = true) }
            .sortedBy { it.name }
            .mapNotNull { file ->
                val parent = file.parentFile ?: return@mapNotNull null
                val legacy = parent.name.startsWith("sd")
                val legacyMarker = file.name.indexOf("_IMGP")
                val dir = if (legacy && legacyMarker > 0) file.name.substring(0, legacyMarker) else parent.name
                val jpg = if (legacy && legacyMarker > 0) file.name.substring(legacyMarker + 1) else file.name
                val sd = if (legacy) parent.name.removePrefix("sd") else parent.parentFile?.name?.removePrefix("sd") ?: "1"
                val thumb = thumbnailFile(sd, dir, jpg).takeIf { it.exists() && it.length() > 0L }
                restoreOrientation(PhotoItem(dir, jpg, null, thumb, file, file.readTakenAt(), storageSlot = sd))
            }
            .toList()

    fun clear() {
        File(cacheDir, "photos").deleteRecursively()
        File(cacheDir, "previews").deleteRecursively()
    }

    fun touch(file: File?) {
        file?.setLastModified(System.currentTimeMillis())
    }

    fun enforceLimit(limitMb: Int, protectedFiles: Set<File>): PreviewCacheUsage {
        val entries = listOf("photos/thumbs", "photos/views", "previews")
            .asSequence()
            .flatMap { File(cacheDir, it).walkTopDown() }
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .map { CacheEntry(it, it.length(), it.lastModified()) }
            .toList()
        var bytes = entries.sumOf { it.bytes }
        var count = entries.size
        if (limitMb > 0) {
            val budget = limitMb * 1_000_000L
            for (entry in entries.sortedBy { it.lastUsed }) {
                if (bytes <= budget) break
                if (entry.file !in protectedFiles && entry.file.delete()) {
                    bytes -= entry.bytes
                    count--
                }
            }
        }
        return PreviewCacheUsage(bytes, count)
    }

    private data class CacheEntry(val file: File, val bytes: Long, val lastUsed: Long)

    private fun cachedPhotoFile(sd: String, dir: String, jpg: String, kind: String): File =
        File(
            File(
                File(cacheDir, "photos/$kind/sd$sd"),
                dir.replace(Regex("[^A-Za-z0-9._-]"), "_"),
            ).apply { mkdirs() },
            jpg.replace(Regex("[^A-Za-z0-9._-]"), "_"),
        )
}

internal fun File.exifOrientation(): Int? = runCatching {
    ExifInterface(absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0).takeIf { it in 1..8 }
}.getOrNull()

private fun File.readTakenAt(): String? = try {
    formatExifDateTime(
        ExifInterface(absolutePath).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: ExifInterface(absolutePath).getAttribute(ExifInterface.TAG_DATETIME)
    )
} catch (_: Exception) {
    null
}

private fun formatExifDateTime(value: String?): String? {
    if (value == null || value.length < 16) return value
    val yy = value.substring(2, 4)
    val month = value.substring(5, 7)
    val day = value.substring(8, 10)
    val hour = value.substring(11, 13)
    val minute = value.substring(14, 16)
    return "$yy/$month/$day $hour:$minute"
}
