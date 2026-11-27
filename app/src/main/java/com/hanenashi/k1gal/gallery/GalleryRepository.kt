package com.hanenashi.k1gal.gallery

import com.hanenashi.k1gal.camera.K1ApiClient
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.util.forEachObject
import com.hanenashi.k1gal.util.isRawName
import com.hanenashi.k1gal.util.numberNearExtension
import com.hanenashi.k1gal.util.orEmpty
import com.hanenashi.k1gal.util.toStringList
import org.json.JSONObject
import java.io.File

internal val photoOrder: Comparator<PhotoItem> =
    compareBy({ it.dir }, { numberNearExtension(it.jpg) }, { it.jpg })

internal class GalleryRepository(
    private val cameraApi: K1ApiClient,
    private val previewCache: PreviewCache,
) {
    fun listPhotos(ip: String, sd: String): List<PhotoItem> {
        val root = cameraApi.listPhotos(ip, sd)
        val photos = mutableListOf<PhotoItem>()

        root.optJSONArray("dirs").orEmpty().forEachObject { directory ->
            val dir = directory.optString("name")
            val files = directory.optJSONArray("files").orEmpty().toStringList()
            val rawByStem = files.filter { it.isRawName() }.associateBy { it.substringBeforeLast(".").uppercase() }

            files.filter { it.endsWith(".JPG", ignoreCase = true) }.forEach { jpg ->
                val raw = rawByStem[jpg.substringBeforeLast(".").uppercase()]
                photos.add(previewCache.restore(sd, PhotoItem(dir, jpg, raw, null, null, null)))
            }
        }

        return photos.sortedWith(photoOrder)
    }

    fun thumbnailInfo(ip: String, sd: String, photo: PhotoItem, thumbnail: File): PhotoItem {
        val metadata = cameraApi.photoInfo(ip, sd, photo.dir, photo.jpg)
        return applyMetadata(photo.copy(thumbFile = thumbnail, loading = false), metadata)
    }

    fun applyMetadata(photo: PhotoItem, metadata: JSONObject): PhotoItem =
        photo.copy(
            takenAt = metadata.optString("datetime").takeIf { it.isNotBlank() },
            av = metadata.optString("av").takeIf { it.isNotBlank() },
            tv = metadata.optString("tv").takeIf { it.isNotBlank() },
            sv = metadata.optString("sv").takeIf { it.isNotBlank() },
            xv = metadata.optString("xv").takeIf { it.isNotBlank() },
            orientation = metadata.optInt("orientation").takeIf { metadata.has("orientation") },
        )
}
