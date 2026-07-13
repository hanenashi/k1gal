package com.hanenashi.k1gal

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.Uri
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs

private const val DEFAULT_CAMERA_IP = "192.168.0.1"
private const val MIN_RAW_BYTES = 1_000_000L
private const val APP_VERSION = "0.3.0"
private const val GITHUB_URL = "https://github.com/hanenashi/k1gal"
private const val PREVIEW_ASPECT_RATIO = 1.5f

private val K1DarkColors = darkColorScheme(
    primary = Color(0xffff8a24),
    onPrimary = Color(0xff180900),
    secondary = Color(0xffffb15c),
    onSecondary = Color(0xff180900),
    surface = Color(0xff0d0b09),
    onSurface = Color(0xfffff1e3),
    surfaceVariant = Color(0xff241913),
    onSurfaceVariant = Color(0xffffc08a),
    outline = Color(0xffb8662c),
)

enum class DownloadKind {
    JPEG,
    RAW,
}

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
)

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val liveClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val photos = mutableStateListOf<PhotoItem>()
    private val selected = mutableStateListOf<PhotoItem>()
    private val rawSizeRequests = mutableSetOf<String>()

    private var cameraIp by mutableStateOf(DEFAULT_CAMERA_IP)
    private var sdCard by mutableStateOf("1")
    private var status by mutableStateOf("Connect to K-1 Wi-Fi, then scan.")
    private var busy by mutableStateOf(false)
    private var viewerIndex by mutableStateOf<Int?>(null)
    private var viewerLoadingLabel by mutableStateOf<String?>(null)
    private var liveViewVisible by mutableStateOf(false)
    private var liveFrame by mutableStateOf<Bitmap?>(null)
    private var liveStatus by mutableStateOf("Live view stopped.")
    private var captureBusy by mutableStateOf(false)
    private var captureStatus by mutableStateOf<String?>(null)
    private var suppressViewerOpenUntilMs = 0L

    @Volatile
    private var liveViewCall: Call? = null
    @Volatile
    private var liveViewSession = 0

    @Volatile
    private var cancelRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()
        cameraIp = guessCameraIp()
        loadExistingCache()
        setContent {
            K1GalApp(
                photos = photos,
                selected = selected,
                cameraIp = cameraIp,
                onCameraIpChange = { cameraIp = it },
                sdCard = sdCard,
                onSdCardChange = { sdCard = it },
                status = status,
                busy = busy,
                viewerIndex = viewerIndex,
                viewerLoadingLabel = viewerLoadingLabel,
                liveViewVisible = liveViewVisible,
                liveFrame = liveFrame,
                liveStatus = liveStatus,
                captureBusy = captureBusy,
                captureStatus = captureStatus,
                onScan = { scanListOnly() },
                onPreviewAll = { previewAll() },
                onStop = { stopWork() },
                onUseCameraIp = {
                    cameraIp = guessCameraIp()
                    status = "Using likely K-1 address: $cameraIp"
                },
                onClear = { clearCache() },
                onDownloadSelected = { downloadSelected(it) },
                onOpen = { openPhoto(it) },
                onCloseViewer = { closeViewer() },
                onViewerPrev = { openAdjacentPhoto(-1) },
                onViewerNext = { openAdjacentPhoto(1) },
                onOpenLiveView = { openLiveView() },
                onCloseLiveView = { closeLiveView() },
                onShoot = { shootFromLiveView() },
                onToggleSelect = { toggleSelection(it) },
                onOpenGithub = { openGithub() },
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigation()
    }

    override fun onStop() {
        closeLiveView()
        super.onStop()
    }

    private fun hideSystemNavigation() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun openGithub() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
    }

    private fun scanListOnly() {
        startWork("Reading K-1 file list...") { ip, sd ->
            val listed = fetchPhotoList(ip, sd)
            runOnUiThread {
                photos.clear()
                selected.clear()
                photos.addAll(listed)
                status = "Listed ${listed.size} JPEGs. Tap a card to fetch its thumbnail, or use All."
            }
        }
    }

    private fun previewAll() {
        val targets = photos.toList()
        if (targets.isEmpty()) {
            status = "Scan first to list camera files."
            return
        }
        startWork("Caching thumbnails...") { ip, sd ->
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                runOnUiThread { status = "Caching ${index + 1}/${targets.size}: ${photo.jpg}" }
                ensureThumbnailCached(ip, sd, photo)
            }
            runOnUiThread {
                status = if (cancelRequested) "Stopped thumbnail caching." else "Cached available thumbnails."
            }
        }
    }

    private fun openPhoto(photo: PhotoItem) {
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
            fetchViewAt(index, openAfterFetch = true)
        }
    }

    private fun closeViewer() {
        viewerIndex = null
        suppressViewerOpenUntilMs = SystemClock.uptimeMillis() + 350L
    }

    private fun canOpenViewer(): Boolean =
        SystemClock.uptimeMillis() >= suppressViewerOpenUntilMs

    private fun openPhotoAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        val cached = photos[index].viewFile
        if (cached != null && cached.exists() && cached.length() > 0L) {
            openCachedViewer(index)
            return
        }
        viewerLoadingLabel = "Loading ${photo.displayName()}..."
        fetchViewAt(index, openAfterFetch = true)
    }

    private fun fetchThumbnailAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        startWork("Fetching thumbnail: ${photo.jpg}") { ip, sd ->
            ensureThumbnailCached(ip, sd, photo)
            runOnUiThread { status = "Thumbnail cached. Tap again to open ${photo.displayName()}." }
        }
    }

    private fun fetchViewAt(index: Int, openAfterFetch: Boolean) {
        val photo = photos.getOrNull(index) ?: return
        startWork("Fetching view: ${photo.jpg}") { ip, sd ->
            val cachedPhoto = ensureViewCached(ip, sd, photo)
            runOnUiThread {
                val freshIndex = photos.indexOfFirst { it.dir == cachedPhoto.dir && it.jpg == cachedPhoto.jpg }
                if (openAfterFetch && freshIndex >= 0 && !cancelRequested) {
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
        val sd = sanitizeSd(sdCard.trim())

        thread(name = "k1gal-raw-size") {
            try {
                bindProcessToWifi()
                val bytes = remoteFileSize(ip, sd, photo.dir, raw)
                if (bytes != null) {
                    runOnUiThread {
                        val freshIndex = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
                        if (freshIndex >= 0) photos[freshIndex] = photos[freshIndex].copy(rawSizeBytes = bytes)
                    }
                }
            } catch (_: Exception) {
                // Size is optional metadata; a failed probe must not block the viewer.
            } finally {
                runOnUiThread { rawSizeRequests.remove(key) }
            }
        }
    }

    private fun openAdjacentPhoto(delta: Int) {
        val current = viewerIndex ?: return
        val target = (current + delta).coerceIn(0, photos.lastIndex)
        if (target != current) openPhotoAt(target)
    }

    private fun openLiveView() {
        if (liveViewVisible) return
        viewerIndex = null
        liveFrame = null
        liveStatus = "Opening K-1 live view..."
        captureStatus = null
        liveViewVisible = true
        val session = ++liveViewSession

        val ip = cameraIp.trim()
        thread(name = "k1gal-live") {
            val request = Request.Builder().url("http://$ip/v1/liveview").build()
            val call = liveClient.newCall(request)
            try {
                bindProcessToWifi()
                if (!liveViewVisible || session != liveViewSession) return@thread
                liveViewCall = call
                call.execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty live-view stream")
                    runOnUiThread { liveStatus = "Live" }
                    readMjpegFrames(body.byteStream(), call) { bitmap ->
                        runOnUiThread {
                            if (liveViewVisible && session == liveViewSession && liveViewCall === call) liveFrame = bitmap
                        }
                    }
                }
            } catch (e: Exception) {
                if (liveViewVisible) {
                    runOnUiThread { liveStatus = "Live view failed: ${e.message ?: "unknown error"}" }
                }
            } finally {
                if (liveViewCall === call) liveViewCall = null
            }
        }
    }

    private fun closeLiveView() {
        liveViewVisible = false
        liveViewSession += 1
        liveViewCall?.cancel()
        liveViewCall = null
        liveFrame = null
        liveStatus = "Live view stopped."
    }

    private fun readMjpegFrames(input: InputStream, call: Call, onFrame: (Bitmap) -> Unit) {
        val frame = ByteArrayOutputStream(128 * 1024)
        var previous = -1
        var collecting = false
        while (liveViewVisible && liveViewCall === call) {
            val current = input.read()
            if (current < 0) break
            if (!collecting) {
                if (previous == 0xff && current == 0xd8) {
                    collecting = true
                    frame.reset()
                    frame.write(0xff)
                    frame.write(0xd8)
                }
            } else {
                frame.write(current)
                if (previous == 0xff && current == 0xd9) {
                    val bytes = frame.toByteArray()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(onFrame)
                    collecting = false
                } else if (frame.size() > 4 * 1024 * 1024) {
                    collecting = false
                    frame.reset()
                }
            }
            previous = current
        }
    }

    private fun shootFromLiveView() {
        if (captureBusy) return
        captureBusy = true
        cancelRequested = false
        captureStatus = "Checking camera..."
        val ip = cameraIp.trim()
        val sd = sanitizeSd(sdCard.trim())

        thread(name = "k1gal-capture") {
            try {
                bindProcessToWifi()
                val camera = JSONObject(httpText("http://$ip/v1/props/camera"))
                if (camera.optString("shootMode") != "single") {
                    error("Set the K-1 drive mode to single")
                }

                val latestUrl = "http://$ip/v1/photos/latest/info?storage=sd$sd"
                val before = JSONObject(httpText(latestUrl))
                val beforeIdentity = "${before.optString("dir")}/${before.optString("file")}"
                runOnUiThread { captureStatus = "Focusing..." }

                val request = Request.Builder()
                    .url("http://$ip/v1/camera/shoot")
                    .post(FormBody.Builder().add("af", "auto").build())
                    .build()
                val shoot = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Shutter HTTP ${response.code}")
                    JSONObject(response.body?.string() ?: error("Empty shutter response"))
                }
                if (shoot.optInt("errCode") != 200) {
                    error(shoot.optString("errMsg", "Camera rejected shutter"))
                }

                runOnUiThread { captureStatus = "Capturing..." }
                var captured: JSONObject? = null
                for (attempt in 0 until 80) {
                    val latest = JSONObject(httpText(latestUrl))
                    val identity = "${latest.optString("dir")}/${latest.optString("file")}"
                    if (latest.optBoolean("captured") && identity != beforeIdentity) {
                        captured = latest
                        break
                    }
                    Thread.sleep(250)
                }
                val metadata = captured ?: error("Timed out waiting for captured photo")
                val capturedFile = metadata.optString("file")
                val jpg = if (capturedFile.endsWith(".JPG", ignoreCase = true)) {
                    capturedFile
                } else {
                    capturedFile.substringBeforeLast(".") + ".JPG"
                }
                val dir = metadata.optString("dir")
                val listed = fetchPhotoList(ip, sd)
                val listedPhoto = listed.firstOrNull { it.dir == dir && it.jpg.equals(jpg, ignoreCase = true) }
                    ?: PhotoItem(dir, jpg, null, null, null, null)
                val thumb = cachedPhotoFile(sd, dir, jpg, "thumbs")
                if (!thumb.exists() || thumb.length() == 0L) {
                    downloadToFile(ip, sd, dir, jpg, thumb, "thumb")
                }
                val newPhoto = listedPhoto.copy(
                    thumbFile = thumb,
                    takenAt = metadata.optString("datetime").takeIf { it.isNotBlank() },
                    av = metadata.optString("av").takeIf { it.isNotBlank() },
                    tv = metadata.optString("tv").takeIf { it.isNotBlank() },
                    sv = metadata.optString("sv").takeIf { it.isNotBlank() },
                    xv = metadata.optString("xv").takeIf { it.isNotBlank() },
                    orientation = metadata.optInt("orientation").takeIf { metadata.has("orientation") },
                )
                runOnUiThread {
                    val combined = photos.filterNot { it.dir == newPhoto.dir && it.jpg == newPhoto.jpg } + newPhoto
                    photos.clear()
                    photos.addAll(combined.sortedWith(compareBy({ it.dir }, { numberNearExtension(it.jpg) }, { it.jpg })))
                    captureStatus = "Captured ${newPhoto.displayName()}"
                }
            } catch (e: Exception) {
                runOnUiThread { captureStatus = "Capture failed: ${e.message ?: "unknown error"}" }
            } finally {
                runOnUiThread { captureBusy = false }
            }
        }
    }

    private fun startWork(initialStatus: String, block: (ip: String, sd: String) -> Unit) {
        if (busy) return
        busy = true
        cancelRequested = false

        val ip = cameraIp.trim()
        val sd = sanitizeSd(sdCard.trim())
        val localIps = wifiLocalIps()
        if (localIps.contains(ip)) {
            status = "$ip is this phone. Use the K-1 gateway, usually ${guessCameraIp()}."
            busy = false
            return
        }
        status = initialStatus

        thread(name = "k1gal-work") {
            try {
                bindProcessToWifi()
                block(ip, sd)
            } catch (e: Exception) {
                if (!cancelRequested) {
                    runOnUiThread { status = "Failed: ${e.message ?: "unknown error"}" }
                }
            } finally {
                runOnUiThread {
                    if (cancelRequested) {
                        val kept = recoverCachedPhotos(sd)
                        status = "Stopped. Kept $kept cached thumbnail(s)."
                    }
                    viewerLoadingLabel = null
                    busy = false
                }
            }
        }
    }

    private fun stopWork() {
        cancelRequested = true
        client.dispatcher.cancelAll()
        status = "Stopping..."
    }

    private fun fetchPhotoList(ip: String, sd: String): List<PhotoItem> {
        val json = httpText("http://$ip/v1/photos?storage=sd$sd")
        val root = JSONObject(json)
        val out = mutableListOf<PhotoItem>()

        root.optJSONArray("dirs").orEmpty().forEachObject { dirObj ->
            val dirName = dirObj.optString("name")
            val files = dirObj.optJSONArray("files").orEmpty().toStringList()
            val rawByStem = files
                .filter { it.isRawName() }
                .associateBy { it.substringBeforeLast(".").uppercase() }

            files.filter { it.endsWith(".JPG", ignoreCase = true) }.forEach { jpg ->
                val thumb = cachedPhotoFile(sd, dirName, jpg, "thumbs").takeIf { it.exists() && it.length() > 0L }
                val view = cachedPhotoFile(sd, dirName, jpg, "views").takeIf { it.exists() && it.length() > 0L }
                out.add(
                    PhotoItem(
                        dir = dirName,
                        jpg = jpg,
                        raw = rawByStem[jpg.substringBeforeLast(".").uppercase()],
                        thumbFile = thumb,
                        viewFile = view,
                        takenAt = null,
                    )
                )
            }
        }

        return out.sortedWith(compareBy({ it.dir }, { numberNearExtension(it.jpg) }, { it.jpg }))
    }

    private fun ensureThumbnailCached(ip: String, sd: String, photo: PhotoItem): PhotoItem {
        val currentIndex = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (currentIndex >= 0) runOnUiThread { photos[currentIndex] = photos[currentIndex].copy(loading = true) }

        val file = cachedPhotoFile(sd, photo.dir, photo.jpg, "thumbs")
        if (!file.exists() || file.length() == 0L) {
            downloadToFile(ip, sd, photo.dir, photo.jpg, file, "thumb")
        }
        val metadata = fetchPhotoInfo(ip, sd, photo)
        val updated = photo.copy(
            thumbFile = file,
            takenAt = metadata.optString("datetime").takeIf { it.isNotBlank() },
            av = metadata.optString("av").takeIf { it.isNotBlank() },
            tv = metadata.optString("tv").takeIf { it.isNotBlank() },
            sv = metadata.optString("sv").takeIf { it.isNotBlank() },
            xv = metadata.optString("xv").takeIf { it.isNotBlank() },
            orientation = metadata.optInt("orientation").takeIf { metadata.has("orientation") },
            loading = false,
        )
        val updatedIndex = photos.indexOfFirst { it.dir == updated.dir && it.jpg == updated.jpg }
        if (updatedIndex >= 0) runOnUiThread { photos[updatedIndex] = updated }
        return updated
    }

    private fun ensureViewCached(ip: String, sd: String, photo: PhotoItem): PhotoItem {
        val currentIndex = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (currentIndex >= 0) runOnUiThread { photos[currentIndex] = photos[currentIndex].copy(loading = true) }

        val file = cachedPhotoFile(sd, photo.dir, photo.jpg, "views")
        if (!file.exists() || file.length() == 0L) {
            downloadToFile(ip, sd, photo.dir, photo.jpg, file, "view")
        }
        var updated = photos.getOrNull(currentIndex) ?: photo
        if (updated.takenAt == null) updated = ensureThumbnailCached(ip, sd, updated)
        updated = updated.copy(viewFile = file, loading = false)
        val updatedIndex = photos.indexOfFirst { it.dir == updated.dir && it.jpg == updated.jpg }
        if (updatedIndex >= 0) runOnUiThread { photos[updatedIndex] = updated }
        return updated
    }

    private fun fetchPhotoInfo(ip: String, sd: String, photo: PhotoItem): JSONObject {
        val url = "http://$ip/v1/photos/${urlPart(photo.dir)}/${urlPart(photo.jpg)}/info?storage=sd$sd"
        return JSONObject(httpText(url))
    }

    private fun downloadToFile(
        ip: String,
        sd: String,
        dir: String,
        fileName: String,
        target: File,
        size: String? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ) {
        if (cancelRequested) return
        val request = Request.Builder().url(photoUrl(ip, sd, dir, fileName, size)).build()
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.download")
        partial.delete()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} for $fileName")
                val body = response.body ?: error("Empty body for $fileName")
                val totalBytes = body.contentLength().takeIf { it >= 0L }
                var downloadedBytes = 0L
                var lastUpdateAt = 0L
                onProgress?.invoke(0L, totalBytes)
                partial.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (cancelRequested) error("Download stopped")
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            val now = SystemClock.uptimeMillis()
                            if (now - lastUpdateAt >= 100L) {
                                onProgress?.invoke(downloadedBytes, totalBytes)
                                lastUpdateAt = now
                            }
                        }
                    }
                }
                onProgress?.invoke(downloadedBytes, totalBytes)
            }
            if (cancelRequested) {
                partial.delete()
                return
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    private fun remoteFileSize(ip: String, sd: String, dir: String, fileName: String): Long? {
        val request = Request.Builder()
            .url(photoUrl(ip, sd, dir, fileName))
            .header("Connection", "close")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.contentLength()?.takeIf { it >= 0L }
        }
    }

    private fun recoverCachedPhotos(sd: String): Int {
        var kept = 0
        photos.indices.forEach { index ->
            val photo = photos[index]
            val thumb = cachedPhotoFile(sd, photo.dir, photo.jpg, "thumbs")
                .takeIf { it.exists() && it.length() > 0L }
            val view = cachedPhotoFile(sd, photo.dir, photo.jpg, "views")
                .takeIf { it.exists() && it.length() > 0L }
            if (thumb != null) kept += 1
            photos[index] = photo.copy(
                thumbFile = thumb,
                viewFile = view,
                loading = false,
            )
        }
        return kept
    }

    private fun downloadSelected(kind: DownloadKind) {
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
                val temp = File(cacheDir, "downloads/${photo.dir}_${photo.jpg}.part").apply { parentFile?.mkdirs() }
                downloadToFile(ip, sd, photo.dir, photo.jpg, temp, "full") { downloaded, total ->
                    runOnUiThread {
                        status = downloadProgressText("JPEG", index + 1, targets.size, downloaded, total)
                    }
                }
                saveDownload(photo.jpg, temp, "image/jpeg")
                savedBytes += temp.length()
                temp.delete()
                saved += 1
            }
            runOnUiThread {
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
                val bytes = downloadRawToDownloads(ip, sd, photo.dir, raw) { downloaded, total ->
                    runOnUiThread {
                        status = downloadProgressText("RAW", index + 1, targets.size, downloaded, total)
                    }
                }
                if (bytes != null) {
                    saved += 1
                    savedBytes += bytes
                }
            }
            runOnUiThread {
                val missingText = if (missing > 0) " $missing selected item(s) had no RAW listed." else ""
                status = "Saved $saved RAW file(s) (${formatMegabytes(savedBytes)}).$missingText"
            }
        }
    }

    private fun downloadRawToDownloads(
        ip: String,
        sd: String,
        dir: String,
        raw: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Long? {
        val temp = File(cacheDir, "raw/$raw.part").apply { parentFile?.mkdirs() }
        downloadToFile(ip, sd, dir, raw, temp, onProgress = onProgress)
        if (temp.length() < MIN_RAW_BYTES) {
            val size = temp.length()
            temp.delete()
            error("$raw downloaded only $size bytes; camera did not return a RAW file")
        }
        val bytes = temp.length()
        saveDownload(raw, temp, "application/octet-stream")
        temp.delete()
        return bytes
    }

    private fun saveDownload(fileName: String, source: File, mimeType: String) {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/k1gal")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create download entry")
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not write download")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun clearCache() {
        File(cacheDir, "photos").deleteRecursively()
        File(cacheDir, "previews").deleteRecursively()
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

    private fun loadExistingCache() {
        File(cacheDir, "photos/views").walkTopDown()
            .filter { it.isFile && it.extension.equals("JPG", ignoreCase = true) }
            .sortedBy { it.name }
            .forEach { file ->
                val parent = file.parentFile ?: return@forEach
                val legacy = parent.name.startsWith("sd")
                val legacyMarker = file.name.indexOf("_IMGP")
                val dir = if (legacy && legacyMarker > 0) file.name.substring(0, legacyMarker) else parent.name
                val jpg = if (legacy && legacyMarker > 0) file.name.substring(legacyMarker + 1) else file.name
                val sd = if (legacy) parent.name.removePrefix("sd") else parent.parentFile?.name?.removePrefix("sd") ?: "1"
                val thumb = cachedPhotoFile(sd, dir, jpg, "thumbs").takeIf { it.exists() && it.length() > 0L }
                photos.add(PhotoItem(dir, jpg, null, thumb, file, file.readTakenAt()))
            }
        if (photos.isNotEmpty()) status = "Loaded ${photos.size} cached previews. Scan to refresh file list."
    }

    private fun toggleSelection(photo: PhotoItem) {
        val selectedIndex = selected.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (selectedIndex >= 0) {
            selected.removeAt(selectedIndex)
        } else {
            selected.add(photo)
        }
    }

    private fun bindProcessToWifi() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork: Network = cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return
        cm.bindProcessToNetwork(wifiNetwork)
    }

    private fun guessCameraIp(): String {
        val localIp = wifiLocalIps().firstOrNull { it.startsWith("192.168.") }
            ?: wifiLocalIps().firstOrNull { it.startsWith("10.") || it.matches(Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) }
        if (localIp != null) {
            val pieces = localIp.split(".")
            if (pieces.size == 4) return pieces.take(3).joinToString(".") + ".1"
        }
        return DEFAULT_CAMERA_IP
    }

    private fun wifiLocalIps(): List<String> {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks
            .filter { network ->
                val capabilities = cm.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false
            }
            .flatMap { network ->
                cm.getLinkProperties(network)?.linkAddresses.orEmpty()
                    .mapNotNull { linkAddress ->
                        val address = linkAddress.address
                        if (address is Inet4Address && !address.isLoopbackAddress) address.hostAddress else null
                    }
            }
            .filterNot { it.startsWith("100.") }
            .distinct()
    }

    private fun httpText(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string() ?: error("Empty response")
        }
    }

    private fun photoUrl(ip: String, sd: String, dir: String, fileName: String, size: String? = null): String {
        val encodedDir = urlPart(dir)
        val encodedFile = urlPart(fileName)
        val sizeQuery = size?.let { "size=${urlPart(it)}&" }.orEmpty()
        return "http://$ip/v1/photos/$encodedDir/$encodedFile?${sizeQuery}storage=sd$sd"
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun K1GalApp(
    photos: List<PhotoItem>,
    selected: List<PhotoItem>,
    cameraIp: String,
    onCameraIpChange: (String) -> Unit,
    sdCard: String,
    onSdCardChange: (String) -> Unit,
    status: String,
    busy: Boolean,
    viewerIndex: Int?,
    viewerLoadingLabel: String?,
    liveViewVisible: Boolean,
    liveFrame: Bitmap?,
    liveStatus: String,
    captureBusy: Boolean,
    captureStatus: String?,
    onScan: () -> Unit,
    onPreviewAll: () -> Unit,
    onStop: () -> Unit,
    onUseCameraIp: () -> Unit,
    onClear: () -> Unit,
    onDownloadSelected: (DownloadKind) -> Unit,
    onOpen: (PhotoItem) -> Unit,
    onCloseViewer: () -> Unit,
    onViewerPrev: () -> Unit,
    onViewerNext: () -> Unit,
    onOpenLiveView: () -> Unit,
    onCloseLiveView: () -> Unit,
    onShoot: () -> Unit,
    onToggleSelect: (PhotoItem) -> Unit,
    onOpenGithub: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var downloadChoiceOpen by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = K1DarkColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xff0b0907)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ActionBar(
                        selectedCount = selected.size,
                        busy = busy,
                        canPreviewAll = photos.isNotEmpty(),
                        onSettings = { settingsOpen = !settingsOpen },
                        onScan = onScan,
                        onPreviewAll = onPreviewAll,
                        onStop = onStop,
                        onClear = onClear,
                        onDownloadSelected = { downloadChoiceOpen = true },
                        onOpenLiveView = onOpenLiveView,
                    )
                    if (settingsOpen) {
                        SettingsPanel(
                            cameraIp = cameraIp,
                            onCameraIpChange = onCameraIpChange,
                            sdCard = sdCard,
                            onSdCardChange = onSdCardChange,
                            busy = busy,
                            onUseCameraIp = onUseCameraIp,
                            onOpenGithub = onOpenGithub,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 118.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(photos, key = { "${it.dir}/${it.jpg}" }) { photo ->
                                val isSelected = selected.any { it.dir == photo.dir && it.jpg == photo.jpg }
                                PhotoCard(
                                    photo = photo,
                                    isSelected = isSelected,
                                    onOpen = { onOpen(photo) },
                                    onToggleSelect = { onToggleSelect(photo) },
                                )
                            }
                        }
                    }
                    FooterStatus(
                        status = status,
                        busy = busy,
                    )
                }
                viewerIndex?.let { index ->
                    photos.getOrNull(index)?.let { photo ->
                        Viewer(
                            photo = photo,
                            index = index,
                            total = photos.size,
                            onClose = onCloseViewer,
                            onPrev = onViewerPrev,
                            onNext = onViewerNext,
                            loadingLabel = viewerLoadingLabel,
                        )
                    }
                }
                if (liveViewVisible) {
                    LiveViewer(
                        frame = liveFrame,
                        status = liveStatus,
                        captureBusy = captureBusy,
                        captureStatus = captureStatus,
                        onClose = onCloseLiveView,
                        onShoot = onShoot,
                    )
                }
                if (downloadChoiceOpen) {
                    DownloadChoiceDialog(
                        onDismiss = { downloadChoiceOpen = false },
                        onChoose = { kind ->
                            downloadChoiceOpen = false
                            onDownloadSelected(kind)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadChoiceDialog(
    onDismiss: () -> Unit,
    onChoose: (DownloadKind) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download selected") },
        text = { Text("Save the cached camera JPEGs or download matching RAW originals.") },
        confirmButton = {
            TextButton(onClick = { onChoose(DownloadKind.RAW) }) {
                Text("RAW")
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(DownloadKind.JPEG) }) {
                Text("JPEG")
            }
        },
    )
}

@Composable
fun ActionBar(
    selectedCount: Int,
    busy: Boolean,
    canPreviewAll: Boolean,
    onSettings: () -> Unit,
    onScan: () -> Unit,
    onPreviewAll: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onDownloadSelected: () -> Unit,
    onOpenLiveView: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color(0xff120c08))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton("Set", onSettings)
        CompactButton(if (busy) "Stop" else "Scan", if (busy) onStop else onScan)
        CompactButton("All", onPreviewAll, enabled = !busy && canPreviewAll)
        CompactButton("Live", onOpenLiveView, enabled = !busy)
        CompactButton("Down", onDownloadSelected, enabled = !busy && selectedCount > 0)
        CompactButton("Clear", onClear, enabled = !busy)
    }
}

@Composable
fun RowScope.CompactButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SettingsPanel(
    cameraIp: String,
    onCameraIpChange: (String) -> Unit,
    sdCard: String,
    onSdCardChange: (String) -> Unit,
    busy: Boolean,
    onUseCameraIp: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xff160f0a))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("k1gal", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("v$APP_VERSION", color = Color(0xffbdbdbd), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cameraIp,
                onValueChange = onCameraIpChange,
                label = { Text("K-1 IP") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = sdCard,
                onValueChange = { if (it in listOf("1", "2", "")) onSdCardChange(it) },
                label = { Text("SD") },
                singleLine = true,
                modifier = Modifier.width(72.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseCameraIp, enabled = !busy) { Text("Use K-1 IP") }
            OutlinedButton(onClick = onOpenGithub) { Text("GitHub") }
        }
        Text(
            "TL;DR: connect to Pentax Wi-Fi, Scan to list files, tap cards for just the previews you need, swipe in viewer, select keepers, then Down saves JPEG previews or RAW originals to Download/k1gal.",
            color = Color(0xffffc08a),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            GITHUB_URL,
            color = Color(0xffff8a24),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FooterStatus(
    status: String,
    busy: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xff1a1009))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xffff8a24),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(status, color = Color(0xffff9d3d), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCard(
    photo: PhotoItem,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onToggleSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xff3a2110) else Color(0xff18120e),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            val cacheFile = photo.thumbFile ?: photo.viewFile
            if (cacheFile != null && cacheFile.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(cacheFile),
                    contentDescription = photo.jpg,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer(rotationZ = orientationRotation(photo.orientation)),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xff2a1a10)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (photo.loading) "..." else "tap", color = Color(0xffffb15c))
                }
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Text(
            text = photo.displayName(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xfffff1e3),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = photo.cardMetaText(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp).padding(bottom = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xffffb15c),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun LiveViewer(
    frame: Bitmap?,
    status: String,
    captureBusy: Boolean,
    captureStatus: String?,
    onClose: () -> Unit,
    onShoot: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "K-1 live view",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = Color(0xffff8a24))
        }
        Text(
            captureStatus ?: status,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .background(Color(0xcc160d07), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color(0xffffb15c),
        )
        OutlinedButton(
            onClick = onClose,
            enabled = !captureBusy,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x99160d07),
                contentColor = Color(0xffffb15c),
            ),
        ) {
            Text("Close")
        }
        Button(
            onClick = onShoot,
            enabled = !captureBusy && frame != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xffd65a19),
                contentColor = Color.White,
            ),
        ) {
            Text(if (captureBusy) "Capturing..." else "Shutter")
        }
    }
}

@Composable
fun Viewer(
    photo: PhotoItem,
    index: Int,
    total: Int,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    loadingLabel: String?,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageBounds by remember { mutableStateOf<Rect?>(null) }
    var lastTapAtMs by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(photo.jpg) {
                val tapSlop = 18.dp.toPx()
                val doubleTapSlop = 80.dp.toPx()
                val swipeThreshold = 44.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedZoomed = scale > 1.05f
                    var dragTotal = Offset.Zero
                    var maxPointers = 1
                    var transformed = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        maxPointers = maxOf(maxPointers, pressed.size)
                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            transformed = true
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            val pan = change.positionChange()
                            if (scale > 1.05f || startedZoomed || transformed) {
                                if (scale > 1f) {
                                    offset += pan
                                    change.consume()
                                }
                            } else {
                                dragTotal += pan
                                if (dragTotal.getDistance() > tapSlop) change.consume()
                            }
                        }
                    }

                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }

                    val bounds = imageBounds
                    val downInsideImage = bounds?.contains(down.position) == true
                    val wasTap = maxPointers == 1 && !transformed && dragTotal.getDistance() <= tapSlop
                    if (wasTap) {
                        val isDoubleTap = downInsideImage &&
                            down.uptimeMillis - lastTapAtMs <= 320L &&
                            (down.position - lastTapPosition).getDistance() <= doubleTapSlop
                        if (isDoubleTap) {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                                offset = Offset.Zero
                            }
                            lastTapAtMs = 0L
                        } else {
                            lastTapAtMs = down.uptimeMillis
                            lastTapPosition = down.position
                            if (!downInsideImage && scale <= 1f) onClose()
                        }
                    } else if (!transformed && !startedZoomed && scale <= 1.05f) {
                        val horizontal = abs(dragTotal.x)
                        val vertical = abs(dragTotal.y)
                        if (horizontal > swipeThreshold && horizontal > vertical * 1.15f) {
                            if (dragTotal.x < 0f) onNext() else onPrev()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val portrait = photo.orientation == 6 || photo.orientation == 8
            val displayAspectRatio = if (portrait) 1f / PREVIEW_ASPECT_RATIO else PREVIEW_ASPECT_RATIO
            val viewportRatio = maxWidth / maxHeight
            val displayWidth = if (viewportRatio > displayAspectRatio) {
                maxHeight * displayAspectRatio
            } else {
                maxWidth
            }
            val displayHeight = if (viewportRatio > displayAspectRatio) {
                maxHeight
            } else {
                maxWidth / displayAspectRatio
            }
            val rawWidth = if (portrait) displayHeight else displayWidth
            val rawHeight = if (portrait) displayWidth else displayHeight

            Box(
                modifier = Modifier
                    .width(displayWidth)
                    .height(displayHeight)
                    .onGloballyPositioned { imageBounds = it.boundsInRoot() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberAsyncImagePainter(photo.viewFile),
                    contentDescription = photo.jpg,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(rawWidth)
                        .height(rawHeight)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = orientationRotation(photo.orientation),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .background(Color(0xcc160d07), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${index + 1}/$total  ${photo.displayName()}",
                color = Color(0xfffff1e3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(photo.exposureMetaText(), color = Color(0xffffb15c))
            Text(photo.viewerTimestamp(), color = Color(0xffd9a477), style = MaterialTheme.typography.bodySmall)
            photo.raw?.let { raw ->
                val size = photo.rawSizeBytes?.let(::formatMegabytes) ?: "checking size..."
                Text("$raw  $size", color = Color(0xffd9a477), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x99160d07),
                contentColor = Color(0xffffb15c),
            ),
        ) {
            Text("Close")
        }
        loadingLabel?.let { label ->
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xdd101010), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xffff8a24),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color(0xfffff1e3))
                }
            }
    }
}

@Composable
fun Header(
    cameraIp: String,
    onCameraIpChange: (String) -> Unit,
    sdCard: String,
    onSdCardChange: (String) -> Unit,
    selectedCount: Int,
    busy: Boolean,
    canPreviewAll: Boolean,
    onScan: () -> Unit,
    onPreviewAll: () -> Unit,
    onStop: () -> Unit,
    onUseCameraIp: () -> Unit,
    onClear: () -> Unit,
    onDownloadSelected: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color(0xff151515))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("k1gal", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("$selectedCount selected", color = Color(0xffe0e0e0), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cameraIp,
                onValueChange = onCameraIpChange,
                label = { Text("K-1 IP") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = sdCard,
                onValueChange = { if (it in listOf("1", "2", "")) onSdCardChange(it) },
                label = { Text("SD") },
                singleLine = true,
                modifier = Modifier.width(72.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) {
                Button(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = onScan) { Text("Scan") }
            }
            OutlinedButton(onClick = onPreviewAll, enabled = !busy && canPreviewAll) { Text("Preview all") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseCameraIp, enabled = !busy) { Text("K-1 IP") }
            Button(onClick = onDownloadSelected, enabled = !busy && selectedCount > 0) { Text("Down") }
            OutlinedButton(onClick = onClear, enabled = !busy) { Text("Clear") }
        }
    }
}

private fun sanitizeSd(sd: String): String = if (sd.trim() == "2") "2" else "1"

private fun numberNearExtension(fileName: String): Int {
    val match = Regex("(\\d{4})(?=\\.[A-Za-z0-9]+$)").find(fileName)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
}

private fun urlPart(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun String.isRawName(): Boolean =
    endsWith(".PEF", ignoreCase = true) || endsWith(".DNG", ignoreCase = true) || endsWith(".RAW", ignoreCase = true)

private fun PhotoItem.displayName(): String = jpg.substringBeforeLast(".")

private fun PhotoItem.cardMetaText(): String =
    compactDate(takenAt) ?: if (raw != null) "RAW" else "JPG"

private fun compactDate(value: String?): String? {
    if (value == null) return null
    Regex("^(\\d{4})[-:](\\d{2})[-:](\\d{2})").find(value)?.let { match ->
        return "${match.groupValues[1].takeLast(2)}/${match.groupValues[2]}/${match.groupValues[3]}"
    }
    return Regex("^(\\d{2})/(\\d{2})/(\\d{2})").find(value)?.value
}

private fun PhotoItem.exposureMetaText(): String =
    listOfNotNull(
        av?.let { "f/$it" },
        tv?.let { formatShutter(it) },
        sv?.let { "ISO $it" },
        xv?.takeUnless { it == "0" || it == "0.0" }?.let { "EV $it" },
    ).joinToString("  ")

private fun PhotoItem.viewerTimestamp(): String =
    takenAt?.replace('T', ' ') ?: dir

private fun downloadProgressText(
    kind: String,
    currentFile: Int,
    fileCount: Int,
    downloadedBytes: Long,
    totalBytes: Long?,
): String {
    val progress = if (totalBytes != null) {
        "${formatMegabytes(downloadedBytes, includeUnit = false)}/${formatMegabytes(totalBytes)}"
    } else {
        formatMegabytes(downloadedBytes)
    }
    return "$kind $currentFile/$fileCount  $progress"
}

private fun formatMegabytes(bytes: Long, includeUnit: Boolean = true): String {
    val value = String.format(Locale.US, "%.1f", bytes / 1_000_000.0)
    return if (includeUnit) "$value MB" else value
}

private fun orientationRotation(orientation: Int?): Float = when (orientation) {
    3 -> 180f
    6 -> 90f
    8 -> 270f
    else -> 0f
}

private fun formatShutter(value: String): String {
    val parts = value.split(".", limit = 2)
    if (parts.size != 2) return value
    val numerator = parts[0].toIntOrNull() ?: return value
    val denominator = parts[1].toIntOrNull() ?: return value
    if (denominator == 0) return value
    return when {
        numerator == 1 -> "1/$denominator"
        denominator == 1 -> "$numerator″"
        else -> "${numerator.toDouble() / denominator}″"
    }
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

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(block)
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
