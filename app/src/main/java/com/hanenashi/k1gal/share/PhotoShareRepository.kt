package com.hanenashi.k1gal.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.hanenashi.k1gal.download.DownloadRepository
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.util.previewRotation
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

internal enum class ShareQuality { PREVIEW, FULL_JPEG }

internal sealed interface ShareEvent {
    data class Ready(val file: File) : ShareEvent
    data class Failed(val message: String) : ShareEvent
}

internal class PhotoShareRepository(cacheDir: File, private val downloads: DownloadRepository) {
    private val shareDir = File(cacheDir, "shared_photos")

    fun prepare(
        photo: PhotoItem,
        quality: ShareQuality,
        ip: String,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long?) -> Unit,
    ): File? {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        shareDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        val directory = File(shareDir, UUID.randomUUID().toString())
        check(directory.mkdirs()) { "Could not create a temporary share file." }
        val target = File(directory, photo.jpg.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        var keep = false
        try {
            if (isCancelled()) return null
            when (quality) {
                ShareQuality.PREVIEW -> copyPreview(photo, target)
                ShareQuality.FULL_JPEG -> if (!downloads.downloadToFile(
                    ip, photo.storageSlot, photo.dir, photo.jpg, target, "full", isCancelled, onProgress,
                )) return null
            }
            if (isCancelled()) return null
            check(target.length() > 0L) { "The JPEG was empty." }
            keep = true
            return target
        } finally {
            if (!keep) directory.deleteRecursively()
        }
    }

    private fun copyPreview(photo: PhotoItem, target: File) {
        val source = photo.viewFile?.takeIf { it.isFile } ?: error("Open the photo again to reload its preview.")
        val exif = photo.viewExifOrientation
        val rotation = photo.previewRotation(exif)
        // BitmapFactory reads the JPEG pixels without applying EXIF.
        val transform = Matrix().apply {
            when (exif) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                6 -> setRotate(90f)
                7 -> {
                    setRotate(270f)
                    postScale(-1f, 1f)
                }
                8 -> setRotate(270f)
            }
            postRotate(rotation)
        }
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: error("Could not read the cached preview.")
        try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
            try {
                check(target.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 93, it) }) {
                    "Could not encode the preview."
                }
            } finally {
                if (rotated !== bitmap) rotated.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
