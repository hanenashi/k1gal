package com.hanenashi.k1gal

import android.app.Application
import android.content.Context
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
import com.hanenashi.k1gal.gallery.PreviewCacheUsage
import com.hanenashi.k1gal.gallery.previewCacheLimitsMb
import com.hanenashi.k1gal.gallery.exifOrientation
import com.hanenashi.k1gal.gallery.photoOrder
import com.hanenashi.k1gal.model.CameraStatus
import com.hanenashi.k1gal.model.DownloadKind
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.model.key
import com.hanenashi.k1gal.share.PhotoShareRepository
import com.hanenashi.k1gal.share.ShareEvent
import com.hanenashi.k1gal.share.ShareQuality
import com.hanenashi.k1gal.util.displayName
import com.hanenashi.k1gal.util.downloadProgressText
import com.hanenashi.k1gal.util.formatMegabytes
import kotlin.concurrent.thread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

private const val DEFAULT_CAMERA_IP = "192.168.0.1"

internal class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val main = Handler(Looper.getMainLooper())
    private val cameraApi = K1ApiClient()
    private val wifiNetwork = WifiNetworkBinder(application.getSystemService(ConnectivityManager::class.java))
    private val previewCache = PreviewCache(
        application.cacheDir,
        application.getSharedPreferences("preview_orientation", Context.MODE_PRIVATE),
    )
    private val cacheSettings = application.getSharedPreferences("preview_cache", Context.MODE_PRIVATE)
    private val gallery = GalleryRepository(cameraApi, previewCache)
    private val downloads = DownloadRepository(cameraApi, application.cacheDir, MediaStoreWriter(application.contentResolver))
    private val photoShares = PhotoShareRepository(application.cacheDir, downloads)
    private val shareResults = Channel<ShareEvent>(Channel.BUFFERED)
    val shareEvents = shareResults.receiveAsFlow()

    @Volatile
    private var shareRequest = 0
    private val liveView = LiveViewController(cameraApi, wifiNetwork)
    private val capture = CaptureController(cameraApi, wifiNetwork)

    private val photos = mutableStateListOf<PhotoItem>()
    private val selected = mutableStateListOf<PhotoItem>()
    val galleryPhotos: List<PhotoItem> get() = photos.toList()
    val selectedPhotos: List<PhotoItem> get() = selected.toList()
    private val rawSizeRequests = mutableSetOf<String>()
    private val orientationAttempts = mutableSetOf<String>()
    private var orientationCandidates = emptyList<PhotoItem>()
    private var orientationInFlight = false
    var selectingRange by mutableStateOf(false)
        private set
    var rangeStart by mutableStateOf<PhotoItem?>(null)
        private set

    var cameraIp by mutableStateOf(guessCameraIp())
        private set
    var cameraStatus by mutableStateOf<CameraStatus>(CameraStatus.Unchecked)
        private set
    private var cameraStatusRequest = 0
    var sdCard by mutableStateOf("1")
        private set
    var status by mutableStateOf("Connect to K-1 Wi-Fi, then scan.")
        private set
    var busy by mutableStateOf(false)
        private set
    var cacheUsage by mutableStateOf<PreviewCacheUsage?>(null)
        private set
    var cacheLimitMb by mutableStateOf(cacheSettings.getInt("limit_mb", 0).takeIf { it in previewCacheLimitsMb } ?: 0)
        private set
    private var cacheMaintenanceRunning = false
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
        refreshCacheUsage()
    }

    fun updateCameraIp(value: String) {
        if (cameraIp == value) return
        cameraIp = value
        cameraStatusRequest++
        cameraStatus = CameraStatus.Unchecked
        orientationAttempts.clear()
        orientationCandidates = emptyList()
    }

    fun updateSdCard(value: String) {
        sdCard = value
    }

    fun useCameraIp() {
        updateCameraIp(guessCameraIp())
        status = "Using likely K-1 address: $cameraIp"
        refreshCameraStatus()
    }

    fun refreshCameraStatus() {
        if (busy || liveViewVisible || captureBusy || cameraStatus == CameraStatus.Checking) return
        val ip = cameraIp.trim()
        if (ip.isEmpty() || ip in wifiNetwork.wifiLocalIps()) {
            cameraStatus = CameraStatus.Unreachable
            return
        }
        val request = ++cameraStatusRequest
        cameraStatus = CameraStatus.Checking
        thread(name = "k1gal-status") {
            val result = try {
                wifiNetwork.bindProcessToWifi()
                cameraApi.deviceStatus(ip)
            } catch (_: Exception) {
                CameraStatus.Unreachable
            }
            postToMain {
                if (request == cameraStatusRequest) cameraStatus = result
            }
        }
    }

    fun scanListOnly() {
        startWork("Reading K-1 file list...", refreshStatus = true) { ip, sd ->
            val listed = gallery.listPhotos(ip, sd)
            postToMain {
                photos.clear()
                clearSelection()
                orientationAttempts.clear()
                photos.addAll(listed)
                status = "Listed ${listed.size} photos."
            }
        }
    }

    fun previewAll() {
        val targets = photos.toList()
        if (targets.isEmpty()) {
            status = "Scan first to list camera files."
            return
        }
        startWork("Caching thumbnails...") { ip, _ ->
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                postToMain { status = "Caching ${index + 1}/${targets.size}: ${photo.jpg}" }
                ensureThumbnailCached(ip, photo.storageSlot, photo)
            }
            postToMain {
                status = if (cancelRequested) "Stopped thumbnail caching." else "Cached available thumbnails."
            }
        }
    }

    fun openPhoto(photo: PhotoItem) {
        if (cacheMaintenanceRunning) return
        if (selectingRange) {
            toggleSelection(photo)
            return
        }
        val index = photos.indexOfFirst { it.key == photo.key }
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
        shareRequest++
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
        startWork("Fetching thumbnail: ${photo.jpg}") { ip, _ ->
            ensureThumbnailCached(ip, photo.storageSlot, photo)
            postToMain { status = "Thumbnail cached. Tap again to open ${photo.displayName()}." }
        }
    }

    private fun fetchViewAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        val sourceViewerIndex = viewerIndex
        startWork("Fetching view: ${photo.jpg}") { ip, _ ->
            val cachedPhoto = ensureViewCached(ip, photo.storageSlot, photo)
            postToMain {
                val freshIndex = photos.indexOfFirst { it.key == cachedPhoto.key }
                if (freshIndex >= 0 && !cancelRequested && viewerIndex == sourceViewerIndex) {
                    openCachedViewer(freshIndex)
                    status = "View cached: ${cachedPhoto.displayName()}."
                }
            }
        }
    }

    private fun openCachedViewer(index: Int) {
        viewerIndex = index
        photos.getOrNull(index)?.let {
            previewCache.touch(it.thumbFile)
            previewCache.touch(it.viewFile)
        }
        fetchRawSizeAt(index)
    }

    fun refreshPreviewOrientations(visible: List<PhotoItem>) {
        orientationCandidates = visible
        probeNextOrientation()
    }

    private fun probeNextOrientation() {
        if (busy || captureBusy || liveViewVisible || orientationInFlight || cameraStatus !is CameraStatus.Connected) return
        val photo = orientationCandidates.firstOrNull {
            it.orientation == null && (it.thumbFile != null || it.viewFile != null) && it.key !in orientationAttempts
        } ?: return
        orientationAttempts.add(photo.key)
        orientationInFlight = true
        val ip = cameraIp.trim()
        thread(name = "k1gal-orientation") {
            val orientation = runCatching {
                cameraApi.previewOrientation(ip, photo.storageSlot, photo.dir, photo.jpg)
            }.getOrNull()
            postToMain {
                orientationInFlight = false
                if (ip == cameraIp.trim() && orientation != null) {
                    updatePhoto(photo) {
                        if (it.orientation != null) it else it.copy(orientation = orientation).also(previewCache::rememberOrientation)
                    }
                }
                probeNextOrientation()
            }
        }
    }

    fun rotatePreview(delta: Int?) {
        if (busy) return
        val index = viewerIndex ?: return
        val photo = photos.getOrNull(index) ?: return
        photos[index] = previewCache.rotate(photo, delta)
    }

    fun sharePhoto(quality: ShareQuality) {
        if (busy || captureBusy) return
        val photo = viewerIndex?.let(photos::getOrNull) ?: return
        val ip = cameraIp.trim()
        val request = ++shareRequest
        busy = true
        cancelRequested = false
        viewerLoadingLabel = if (quality == ShareQuality.PREVIEW) "Preparing preview…" else "Fetching full JPEG…"
        thread(name = "k1gal-share") {
            val result = runCatching {
                if (quality == ShareQuality.FULL_JPEG) {
                    check(ip.isNotEmpty() && ip !in wifiNetwork.wifiLocalIps()) { "Connect to the K-1 Wi-Fi for the full JPEG." }
                    wifiNetwork.bindProcessToWifi()
                }
                photoShares.prepare(photo, quality, ip, isCancelled = { isCancelled() || request != shareRequest }) { bytes, total ->
                    postToMain {
                        if (request == shareRequest) {
                            viewerLoadingLabel = downloadProgressText("JPEG", 1, 1, bytes, total)
                        }
                    }
                }
            }
            postToMain {
                viewerLoadingLabel = null
                busy = false
                if (request == shareRequest && !cancelRequested) {
                    result.onSuccess { it?.let { file -> shareResults.trySend(ShareEvent.Ready(file)) } }
                    result.onFailure { shareResults.trySend(ShareEvent.Failed(it.message ?: "Could not prepare the photo.")) }
                }
                probeNextOrientation()
            }
        }
    }

    private fun fetchRawSizeAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        val raw = photo.raw ?: return
        if (photo.rawSizeBytes != null) return
        val key = photo.key
        if (!rawSizeRequests.add(key)) return
        val ip = cameraIp.trim()

        thread(name = "k1gal-raw-size") {
            try {
                wifiNetwork.bindProcessToWifi()
                val bytes = cameraApi.remoteFileSize(ip, photo.storageSlot, photo.dir, raw)
                if (bytes != null) {
                    postToMain {
                        updatePhoto(photo) { it.copy(rawSizeBytes = bytes) }
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
        if (busy || captureBusy) return
        val current = viewerIndex ?: return
        val target = (current + delta).coerceIn(0, photos.lastIndex)
        if (target != current) openPhotoAt(target)
    }

    fun openLiveView() {
        if (liveViewVisible || busy) return
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
                        ?: PhotoItem(dir, jpg, null, null, null, null, storageSlot = sd)
                    val thumb = previewCache.thumbnailFile(sd, dir, jpg)
                    if (!thumb.exists() || thumb.length() == 0L) {
                        downloads.downloadToFile(ip, sd, dir, jpg, thumb, "thumb", ::isCancelled)
                    }
                    val newPhoto = gallery.applyMetadata(
                        photo.copy(thumbFile = thumb, thumbExifOrientation = thumb.exifOrientation()),
                        metadata,
                    )
                    postToMain {
                        val combined = photos.filterNot { it.key == newPhoto.key } + newPhoto
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
                    maintainPreviewCache { captureBusy = false }
                }
            },
        )
    }

    private fun startWork(
        initialStatus: String,
        refreshStatus: Boolean = false,
        block: (ip: String, sd: String) -> Unit,
    ) {
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
                    val stopped = cancelRequested
                    if (stopped) {
                        photos.replaceAll(previewCache::restore)
                    }
                    viewerLoadingLabel = null
                    maintainPreviewCache {
                        if (stopped) {
                            val kept = photos.count { it.thumbFile != null }
                            status = "Stopped. Kept $kept cached thumbnail(s)."
                        }
                        busy = false
                        if (refreshStatus && !stopped) refreshCameraStatus()
                        probeNextOrientation()
                    }
                }
            }
        }
    }

    fun stopWork() {
        if (!busy || cacheMaintenanceRunning) return
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
        val withMetadata = if (photo.takenAt == null || photo.orientation == null) ensureThumbnailCached(ip, sd, photo) else photo
        val updated = withMetadata.copy(viewFile = file, viewExifOrientation = file.exifOrientation(), loading = false)
        postToMain { updatePhoto(photo) { updated.copy(rawSizeBytes = it.rawSizeBytes) } }
        return updated
    }

    private fun updatePhoto(photo: PhotoItem, update: (PhotoItem) -> PhotoItem) {
        val index = photos.indexOfFirst { it.key == photo.key }
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
        startWork("Saving ${targets.size} JPEG file(s)...") { ip, _ ->
            var saved = 0
            var savedBytes = 0L
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                val bytes = downloads.saveJpeg(ip, photo.storageSlot, photo, ::isCancelled) { downloaded, total ->
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
        startWork("Downloading ${targets.size} RAW file(s)...") { ip, _ ->
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
                val bytes = downloads.saveRaw(ip, photo.storageSlot, photo.dir, raw, ::isCancelled) { downloaded, total ->
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
        if (busy || captureBusy) return
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
        clearSelection()
        orientationAttempts.clear()
        orientationCandidates = emptyList()
        viewerIndex = null
        refreshCacheUsage()
        status = "Preview cache cleared."
    }

    fun changeCacheLimit(limitMb: Int) {
        if (busy || captureBusy || limitMb !in previewCacheLimitsMb || cacheLimitMb == limitMb) return
        cacheLimitMb = limitMb
        cacheSettings.edit().putInt("limit_mb", limitMb).apply()
        refreshCacheUsage()
    }

    private fun refreshCacheUsage() {
        busy = true
        maintainPreviewCache { busy = false }
    }

    private fun maintainPreviewCache(onComplete: () -> Unit) {
        cacheMaintenanceRunning = true
        val current = viewerIndex?.let(photos::getOrNull)
        val protected = setOfNotNull(current?.thumbFile, current?.viewFile)
        val limit = cacheLimitMb
        val cached = photos.toList()
        thread(name = "k1gal-cache-budget") {
            val result = runCatching {
                val usage = previewCache.enforceLimit(limit, protected)
                val missing = cached.flatMap { listOfNotNull(it.thumbFile, it.viewFile) }
                    .filterNot { it.isFile }.toSet()
                usage to missing
            }
            postToMain {
                result.onSuccess { (usage, missing) ->
                    cacheUsage = usage
                    if (missing.isNotEmpty()) {
                        photos.replaceAll { photo ->
                            val thumbRemoved = photo.thumbFile in missing
                            val viewRemoved = photo.viewFile in missing
                            if (!thumbRemoved && !viewRemoved) photo else photo.copy(
                                thumbFile = if (thumbRemoved) null else photo.thumbFile,
                                viewFile = if (viewRemoved) null else photo.viewFile,
                                thumbExifOrientation = if (thumbRemoved) null else photo.thumbExifOrientation,
                                viewExifOrientation = if (viewRemoved) null else photo.viewExifOrientation,
                            )
                        }
                    }
                }
                result.onFailure { cacheUsage = null }
                cacheMaintenanceRunning = false
                onComplete()
            }
        }
    }

    fun toggleSelection(photo: PhotoItem) {
        if (busy) return
        if (selectingRange) {
            val start = rangeStart
            if (start == null) {
                rangeStart = photo
                return
            }
            val first = photos.indexOfFirst { it.key == start.key }
            val last = photos.indexOfFirst { it.key == photo.key }
            if (first >= 0 && last >= 0) {
                val selectedKeys = selected.mapTo(mutableSetOf()) { it.key }
                val range = photos.subList(minOf(first, last), maxOf(first, last) + 1)
                selected.addAll(range.filter { selectedKeys.add(it.key) })
            }
            cancelRange()
            return
        }
        val selectedIndex = selected.indexOfFirst { it.key == photo.key }
        if (selectedIndex >= 0) {
            selected.removeAt(selectedIndex)
        } else {
            selected.add(photo)
        }
    }

    fun selectAll() {
        if (busy) return
        selected.clear()
        selected.addAll(photos)
        cancelRange()
    }

    fun beginRange() {
        if (busy || photos.isEmpty()) return
        rangeStart = null
        selectingRange = true
    }

    fun cancelRange() {
        selectingRange = false
        rangeStart = null
    }

    fun clearSelection() {
        selected.clear()
        cancelRange()
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
        shareResults.close()
        main.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
