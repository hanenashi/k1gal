package com.hanenashi.k1gal.gallery

import android.media.ExifInterface
import com.hanenashi.k1gal.model.PhotoItem
import java.io.File

internal class PreviewCache(private val cacheDir: File) {
    fun thumbnailFile(sd: String, dir: String, jpg: String): File =
        cachedPhotoFile(sd, dir, jpg, "thumbs")

    fun viewFile(sd: String, dir: String, jpg: String): File =
        cachedPhotoFile(sd, dir, jpg, "views")

    fun restore(sd: String, photo: PhotoItem): PhotoItem =
        photo.copy(
            thumbFile = thumbnailFile(sd, photo.dir, photo.jpg).takeIf { it.exists() && it.length() > 0L },
            viewFile = viewFile(sd, photo.dir, photo.jpg).takeIf { it.exists() && it.length() > 0L },
            loading = false,
        )

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
                PhotoItem(dir, jpg, null, thumb, file, file.readTakenAt())
            }
            .toList()

    fun clear() {
        File(cacheDir, "photos").deleteRecursively()
        File(cacheDir, "previews").deleteRecursively()
    }

    private fun cachedPhotoFile(sd: String, dir: String, jpg: String, kind: String): File =
        File(
            File(
                File(cacheDir, "photos/$kind/sd$sd"),
                dir.replace(Regex("[^A-Za-z0-9._-]"), "_"),
            ).apply { mkdirs() },
            jpg.replace(Regex("[^A-Za-z0-9._-]"), "_"),
        )
}

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
