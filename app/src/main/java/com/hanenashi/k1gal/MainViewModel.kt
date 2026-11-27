package com.hanenashi.k1gal

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.hanenashi.k1gal.camera.CaptureController
import com.hanenashi.k1gal.camera.K1ApiClient
import com.hanenashi.k1gal.camera.LiveViewController
import com.hanenashi.k1gal.camera.WifiNetworkBinder
import com.hanenashi.k1gal.download.DownloadRepository
import com.hanenashi.k1gal.download.MediaStoreWriter
import com.hanenashi.k1gal.gallery.GalleryRepository
import com.hanenashi.k1gal.gallery.PreviewCache
import com.hanenashi.k1gal.gallery.photoOrder
import com.hanenashi.k1gal.model.DownloadKind
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.util.displayName
import com.hanenashi.k1gal.util.downloadProgressText
import com.hanenashi.k1gal.util.formatMegabytes
import kotlin.concurrent.thread

private const val DEFAULT_CAMERA_IP = "192.168.0.1"

internal class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val main = Handler(Looper.getMainLooper())
    private val cameraApi = K1ApiClient()
    private val wifiNetwork = WifiNetworkBinder(application.getSystemService(ConnectivityManager::class.java))
    private val previewCache = PreviewCache(application.cacheDir)
    private val gallery = GalleryRepository(cameraApi, previewCache)
    private val downloads = DownloadRepository(cameraApi, application.cacheDir, MediaStoreWriter(application.contentResolver))
    private val liveView = LiveViewController(cameraApi, wifiNetwork)
    private val capture = CaptureController(cameraApi, wifiNetwork)

    private val photos = mutableStateListOf<PhotoItem>()
    private val selected = mutableStateListOf<PhotoItem>()
    val galleryPhotos: List<PhotoItem> get() = photos.toList()
    val selectedPhotos: List<PhotoItem> get() = selected.toList()
    private val rawSizeRequests = mutableSetOf<String>()

    var cameraIp by mutableStateOf(guessCameraIp())
        private set
    var sdCard by mutableStateOf("1")
        private set
    var status by mutableStateOf("Connect to K-1 Wi-Fi, then scan.")
        private set
    var busy by mutableStateOf(false)
        private set
    var viewerIndex by mutableStateOf<Int?>(null)
        private set
    var viewerLoadingLabel by mutableStateOf<String?>(null)
        private set
    var liveViewVisible by mutableStateOf(false)
        private set
    var liveFrame by mutableStateOf<Bitmap?>(null)
        private set
    var liveStatus by mutableStateOf("Live view stopped.")
        private set
    var captureBusy by mutableStateOf(false)
        private set
    var captureStatus by mutableStateOf<String?>(null)
        private set

    private var suppressViewerOpenUntilMs = 0L

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var cleared = false

    init {
        photos.addAll(previewCache.loadExisting())
        if (photos.isNotEmpty()) status = "Loaded ${photos.size} cached previews. Scan to refresh file list."
    }

    fun updateCameraIp(value: String) {
        cameraIp = value
    }

    fun updateSdCard(value: String) {
        sdCard = value
    }

    fun useCameraIp() {
        cameraIp = guessCameraIp()
        status = "Using likely K-1 address: $cameraIp"
    }

    fun scanListOnly() {
        startWork("Reading K-1 file list...") { ip, sd ->
            val listed = gallery.listPhotos(ip, sd)
            postToMain {
                photos.clear()
                selected.clear()
                photos.addAll(listed)
                status = "Listed ${listed.size} JPEGs. Tap a card to fetch its thumbnail, or use All."
            }
        }
    }

    fun previewAll() {
        val targets = photos.toList()
        if (targets.isEmpty()) {
            status = "Scan first to list camera files."
            return
        }
        startWork("Caching thumbnails...") { ip, sd ->
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                postToMain { status = "Caching ${index + 1}/${targets.size}: ${photo.jpg}" }
                ensureThumbnailCached(ip, sd, photo)
            }
            postToMain {
                status = if (cancelRequested) "Stopped thumbnail caching." else "Cached available thumbnails."
            }
        }
    }

    fun openPhoto(photo: PhotoItem) {
        val index = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (index < 0) return
        val current = photos[index]
        val view = current.viewFile
        if (view != null && view.exists() && view.length() > 0L) {
            if (!canOpenViewer()) return
            openCachedViewer(index)
            return
        }
        val thumb = current.thumbFile
        if (thumb == null || !thumb.exists() || thumb.length() == 0L) {
            fetchThumbnailAt(index)
        } else {
            fetchViewAt(index)
        }
    }

    fun closeViewer() {
        viewerIndex = null
        suppressViewerOpenUntilMs = SystemClock.uptimeMillis() + 350L
    }

    private fun canOpenViewer(): Boolean =
        SystemClock.uptimeMillis() >= suppressViewerOpenUntilMs

    private fun openPhotoAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        val cached = photo.viewFile
        if (cached != null && cached.exists() && cached.length() > 0L) {
            openCachedViewer(index)
            return
        }
        viewerLoadingLabel = "Loading ${photo.displayName()}..."
        fetchViewAt(index)
    }

    private fun fetchThumbnailAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        startWork("Fetching thumbnail: ${photo.jpg}") { ip, sd ->
            ensureThumbnailCached(ip, sd, photo)
            postToMain { status = "Thumbnail cached. Tap again to open ${photo.displayName()}." }
        }
    }

    private fun fetchViewAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        startWork("Fetching view: ${photo.jpg}") { ip, sd ->
            val cachedPhoto = ensureViewCached(ip, sd, photo)
            postToMain {
                val freshIndex = photos.indexOfFirst { it.dir == cachedPhoto.dir && it.jpg == cachedPhoto.jpg }
                if (freshIndex >= 0 && !cancelRequested) {
                    openCachedViewer(freshIndex)
                    status = "View cached: ${cachedPhoto.displayName()}."
                }
            }
        }
    }

    private fun openCachedViewer(index: Int) {
        viewerIndex = index
        fetchRawSizeAt(index)
    }

    private fun fetchRawSizeAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        val raw = photo.raw ?: return
        if (photo.rawSizeBytes != null) return
        val key = "${photo.dir}/$raw"
        if (!rawSizeRequests.add(key)) return
        val ip = cameraIp.trim()
        val sd = sanitizeSd()

        thread(name = "k1gal-raw-size") {
            try {
                wifiNetwork.bindProcessToWifi()
                val bytes = cameraApi.remoteFileSize(ip, sd, photo.dir, raw)
                if (bytes != null) {
                    postToMain {
                        val freshIndex = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
                        if (freshIndex >= 0) photos[freshIndex] = photos[freshIndex].copy(rawSizeBytes = bytes)
                    }
                }
            } catch (_: Exception) {
                // Size is optional metadata; a failed probe must not block the viewer.
            } finally {
                postToMain { rawSizeRequests.remove(key) }
            }
        }
    }

    fun openAdjacentPhoto(delta: Int) {
        val current = viewerIndex ?: return
        val target = (current + delta).coerceIn(0, photos.lastIndex)
        if (target != current) openPhotoAt(target)
    }

    fun openLiveView() {
        if (liveViewVisible) return
        viewerIndex = null
        liveFrame = null
        liveStatus = "Opening K-1 live view..."
        captureStatus = null
        liveViewVisible = true
        liveView.start(
            cameraIp.trim(),
            onReady = { liveStatus = "Live" },
            onFrame = { liveFrame = it },
            onFailure = {
                liveFrame = null
                liveStatus = "Live view failed: ${it.message ?: "unknown error"}"
            },
        )
    }

    fun closeLiveView() {
        liveView.stop()
        liveViewVisible = false
        liveFrame = null
        liveStatus = "Live view stopped."
    }

    fun shootFromLiveView() {
        if (!liveViewVisible || liveFrame == null || captureBusy || busy) return
        cancelRequested = false
        captureStatus = "Checking camera..."
        val ip = cameraIp.trim()
        val sd = sanitizeSd()
        captureBusy = capture.start(
            ip,
            sd,
            onStatus = { if (!cleared) captureStatus = it },
            onCaptured = { metadata ->
                if (!cleared) {
                    val file = metadata.optString("file")
                    val jpg = if (file.endsWith(".JPG", ignoreCase = true)) file else file.substringBeforeLast(".") + ".JPG"
                    val dir = metadata.optString("dir")
                    val listed = gallery.listPhotos(ip, sd)
                    val photo = listed.firstOrNull { it.dir == dir && it.jpg.equals(jpg, ignoreCase = true) }
                        ?: PhotoItem(dir, jpg, null, null, null, null)
                    val thumb = previewCache.thumbnailFile(sd, dir, jpg)
                    if (!thumb.exists() || thumb.length() == 0L) {
                        downloads.downloadToFile(ip, sd, dir, jpg, thumb, "thumb", ::isCancelled)
                    }
                    val newPhoto = gallery.applyMetadata(photo.copy(thumbFile = thumb), metadata)
                    postToMain {
                        val combined = photos.filterNot { it.dir == newPhoto.dir && it.jpg == newPhoto.jpg } + newPhoto
                        photos.clear()
                        photos.addAll(combined.sortedWith(photoOrder))
                        captureStatus = "Captured ${newPhoto.displayName()}"
                    }
                }
            },
            onFinished = { result ->
                if (!cleared) {
                    result.exceptionOrNull()?.let {
                        captureStatus = "Capture failed: ${it.message ?: "unknown error"}"
                    }
                    captureBusy = false
                }
            },
        )
    }

    private fun startWork(initialStatus: String, block: (ip: String, sd: String) -> Unit) {
        if (busy || captureBusy) return
        busy = true
        cancelRequested = false

        val ip = cameraIp.trim()
        val sd = sanitizeSd()
        val localIps = wifiNetwork.wifiLocalIps()
        if (localIps.contains(ip)) {
            status = "$ip is this phone. Use the K-1 gateway, usually ${guessCameraIp()}."
            busy = false
            return
        }
        status = initialStatus

        thread(name = "k1gal-work") {
            try {
                wifiNetwork.bindProcessToWifi()
                block(ip, sd)
            } catch (e: Exception) {
                if (!cancelRequested) {
                    postToMain { status = "Failed: ${e.message ?: "unknown error"}" }
                }
            } finally {
                postToMain {
                    if (cancelRequested) {
                        photos.replaceAll { previewCache.restore(sd, it) }
                        val kept = photos.count { it.thumbFile != null }
                        status = "Stopped. Kept $kept cached thumbnail(s)."
                    }
                    viewerLoadingLabel = null
                    busy = false
                }
            }
        }
    }

    fun stopWork() {
        cancelRequested = true
        cameraApi.cancelCalls()
        status = "Stopping..."
    }

    private fun ensureThumbnailCached(ip: String, sd: String, photo: PhotoItem): PhotoItem {
        postToMain { updatePhoto(photo) { it.copy(loading = true) } }
        val file = previewCache.thumbnailFile(sd, photo.dir, photo.jpg)
        if (!file.exists() || file.length() == 0L) {
            if (!downloads.downloadToFile(ip, sd, photo.dir, photo.jpg, file, "thumb", ::isCancelled)) {
                return photo
            }
        }
        val updated = gallery.thumbnailInfo(ip, sd, photo, file)
        postToMain { updatePhoto(photo) { updated.copy(rawSizeBytes = it.rawSizeBytes) } }
        return updated
    }

    private fun ensureViewCached(ip: String, sd: String, photo: PhotoItem): PhotoItem {
        postToMain { updatePhoto(photo) { it.copy(loading = true) } }
        val file = previewCache.viewFile(sd, photo.dir, photo.jpg)
        if (!file.exists() || file.length() == 0L) {
            if (!downloads.downloadToFile(ip, sd, photo.dir, photo.jpg, file, "view", ::isCancelled)) {
                return photo
            }
        }
        val withMetadata = if (photo.takenAt == null) ensureThumbnailCached(ip, sd, photo) else photo
        val updated = withMetadata.copy(viewFile = file, loading = false)
        postToMain { updatePhoto(photo) { updated.copy(rawSizeBytes = it.rawSizeBytes) } }
        return updated
    }

    private fun updatePhoto(photo: PhotoItem, update: (PhotoItem) -> PhotoItem) {
        val index = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (index >= 0) photos[index] = update(photos[index])
    }

    fun downloadSelected(kind: DownloadKind) {
        when (kind) {
            DownloadKind.JPEG -> downloadSelectedJpegs()
            DownloadKind.RAW -> downloadSelectedRaws()
        }
    }

    private fun downloadSelectedJpegs() {
        val targets = selected.toList()
        if (targets.isEmpty()) return
        startWork("Saving ${targets.size} JPEG file(s)...") { ip, sd ->
            var saved = 0
            var savedBytes = 0L
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                val bytes = downloads.saveJpeg(ip, sd, photo, ::isCancelled) { downloaded, total ->
                    postToMain {
                        status = downloadProgressText("JPEG", index + 1, targets.size, downloaded, total)
                    }
                } ?: return@startWork
                savedBytes += bytes
                saved += 1
            }
            postToMain {
                status = "Saved $saved JPEG file(s) (${formatMegabytes(savedBytes)}) to Download/k1gal."
            }
        }
    }

    private fun downloadSelectedRaws() {
        val targets = selected.toList()
        if (targets.isEmpty()) return
        startWork("Downloading ${targets.size} RAW file(s)...") { ip, sd ->
            var saved = 0
            var savedBytes = 0L
            var missing = 0
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                val raw = photo.raw
                if (raw == null) {
                    missing += 1
                    return@forEachIndexed
                }
                val bytes = downloads.saveRaw(ip, sd, photo.dir, raw, ::isCancelled) { downloaded, total ->
                    postToMain {
                        status = downloadProgressText("RAW", index + 1, targets.size, downloaded, total)
                    }
                } ?: return@startWork
                saved += 1
                savedBytes += bytes
            }
            postToMain {
                val missingText = if (missing > 0) " $missing selected item(s) had no RAW listed." else ""
                status = "Saved $saved RAW file(s) (${formatMegabytes(savedBytes)}).$missingText"
            }
        }
    }

    fun clearCache() {
        previewCache.clear()
        photos.replaceAll {
            it.copy(
                thumbFile = null,
                viewFile = null,
                takenAt = null,
                av = null,
                tv = null,
                sv = null,
                xv = null,
                orientation = null,
                loading = false,
            )
        }
        selected.clear()
        viewerIndex = null
        status = "Preview cache cleared."
    }

    fun toggleSelection(photo: PhotoItem) {
        val selectedIndex = selected.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (selectedIndex >= 0) {
            selected.removeAt(selectedIndex)
        } else {
            selected.add(photo)
        }
    }

    private fun guessCameraIp(): String {
        val localIps = wifiNetwork.wifiLocalIps()
        val localIp = localIps.firstOrNull { it.startsWith("192.168.") }
            ?: localIps.firstOrNull { it.startsWith("10.") || it.matches(Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) }
        if (localIp != null) {
            val pieces = localIp.split(".")
            if (pieces.size == 4) return pieces.take(3).joinToString(".") + ".1"
        }
        return DEFAULT_CAMERA_IP
    }

    private fun sanitizeSd(): String = if (sdCard.trim() == "2") "2" else "1"

    private fun isCancelled(): Boolean = cancelRequested || cleared

    private fun postToMain(action: () -> Unit) {
        main.post { if (!cleared) action() }
    }

    override fun onCleared() {
        cleared = true
        cancelRequested = true
        liveView.stop()
        cameraApi.cancelCalls()
        main.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
