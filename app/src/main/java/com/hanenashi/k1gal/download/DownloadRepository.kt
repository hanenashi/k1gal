package com.hanenashi.k1gal.download

import android.os.SystemClock
import com.hanenashi.k1gal.camera.K1ApiClient
import com.hanenashi.k1gal.model.PhotoItem
import java.io.File

private const val MIN_RAW_BYTES = 1_000_000L

internal class DownloadRepository(
    private val cameraApi: K1ApiClient,
    private val cacheDir: File,
    private val mediaStore: MediaStoreWriter,
) {
    fun downloadToFile(
        ip: String,
        sd: String,
        dir: String,
        fileName: String,
        target: File,
        size: String? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ): Boolean {
        if (isCancelled()) return false
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.download")
        partial.delete()
        try {
            cameraApi.readPhoto(ip, sd, dir, fileName, size) { stream, totalBytes ->
                var downloadedBytes = 0L
                var lastUpdateAt = 0L
                onProgress?.invoke(0L, totalBytes)
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (!isCancelled()) {
                        val count = stream.read(buffer)
                        if (count < 0 || isCancelled()) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUpdateAt >= 100L) {
                            onProgress?.invoke(downloadedBytes, totalBytes)
                            lastUpdateAt = now
                        }
                    }
                }
                if (!isCancelled()) onProgress?.invoke(downloadedBytes, totalBytes)
            }
            if (isCancelled()) return false
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) partial.copyTo(target, overwrite = true)
            return true
        } finally {
            partial.delete()
        }
    }

    fun saveJpeg(
        ip: String,
        sd: String,
        photo: PhotoItem,
        isCancelled: () -> Boolean,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Long? {
        val temp = File(cacheDir, "downloads/${photo.dir}_${photo.jpg}.part")
        try {
            if (!downloadToFile(ip, sd, photo.dir, photo.jpg, temp, "full", isCancelled, onProgress)) return null
            if (isCancelled()) return null
            mediaStore.save(photo.jpg, temp, "image/jpeg")
            return temp.length()
        } finally {
            temp.delete()
        }
    }

    fun saveRaw(
        ip: String,
        sd: String,
        dir: String,
        raw: String,
        isCancelled: () -> Boolean,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Long? {
        val temp = File(cacheDir, "raw/$raw.part")
        try {
            if (!downloadToFile(ip, sd, dir, raw, temp, isCancelled = isCancelled, onProgress = onProgress)) return null
            if (isCancelled()) return null
            val bytes = temp.length()
            if (bytes < MIN_RAW_BYTES) {
                error("$raw downloaded only $bytes bytes; camera did not return a RAW file")
            }
            mediaStore.save(raw, temp, "application/octet-stream")
            return bytes
        } finally {
            temp.delete()
        }
    }
}
