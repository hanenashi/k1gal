package com.hanenashi.k1gal

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.Uri
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs

private const val DEFAULT_CAMERA_IP = "192.168.0.1"
private const val MIN_RAW_BYTES = 1_000_000L
private const val APP_VERSION = "0.2.2"
private const val GITHUB_URL = "https://github.com/hanenashi/k1gal"

private val K1DarkColors = darkColorScheme(
    primary = Color(0xff9f7aea),
    onPrimary = Color.White,
    surface = Color(0xff101010),
    onSurface = Color(0xffeeeeee),
    surfaceVariant = Color(0xff242424),
    onSurfaceVariant = Color(0xffd0d0d0),
    outline = Color(0xff8c8794),
)

data class PhotoItem(
    val dir: String,
    val jpg: String,
    val raw: String?,
    val cacheFile: File?,
    val takenAt: String?,
    val loading: Boolean = false,
)

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val photos = mutableStateListOf<PhotoItem>()
    private val selected = mutableStateListOf<PhotoItem>()

    private var cameraIp by mutableStateOf(DEFAULT_CAMERA_IP)
    private var sdCard by mutableStateOf("1")
    private var status by mutableStateOf("Connect to K-1 Wi-Fi, then scan.")
    private var busy by mutableStateOf(false)
    private var viewerIndex by mutableStateOf<Int?>(null)
    private var viewerLoadingLabel by mutableStateOf<String?>(null)

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
                onScan = { scanListOnly() },
                onPreviewAll = { previewAll() },
                onStop = { stopWork() },
                onUseCameraIp = {
                    cameraIp = guessCameraIp()
                    status = "Using likely K-1 address: $cameraIp"
                },
                onClear = { clearCache() },
                onDownloadSelected = { downloadSelectedRaws() },
                onOpen = { openPhoto(it) },
                onCloseViewer = { viewerIndex = null },
                onViewerPrev = { openAdjacentPhoto(-1) },
                onViewerNext = { openAdjacentPhoto(1) },
                onToggleSelect = { toggleSelection(it) },
                onOpenGithub = { openGithub() },
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigation()
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
                status = "Listed ${listed.size} JPEGs. Tap a card to fetch one preview, or Preview all."
            }
        }
    }

    private fun previewAll() {
        val targets = photos.toList()
        if (targets.isEmpty()) {
            status = "Scan first to list camera files."
            return
        }
        startWork("Caching previews...") { ip, sd ->
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                runOnUiThread { status = "Caching ${index + 1}/${targets.size}: ${photo.jpg}" }
                ensurePreviewCached(ip, sd, photo)
            }
            runOnUiThread {
                status = if (cancelRequested) "Stopped preview caching." else "Cached available previews."
            }
        }
    }

    private fun openPhoto(photo: PhotoItem) {
        val index = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (index < 0) return
        val cached = photos[index].cacheFile
        if (cached != null && cached.exists() && cached.length() > 0L) {
            viewerIndex = index
            return
        }
        fetchPreviewAt(index, openAfterFetch = false)
    }

    private fun openPhotoAt(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        val cached = photos[index].cacheFile
        if (cached != null && cached.exists() && cached.length() > 0L) {
            viewerIndex = index
            return
        }
        viewerLoadingLabel = "Loading ${photo.displayName()}..."
        fetchPreviewAt(index, openAfterFetch = true)
    }

    private fun fetchPreviewAt(index: Int, openAfterFetch: Boolean) {
        val photo = photos.getOrNull(index) ?: return
        startWork("Fetching preview: ${photo.jpg}") { ip, sd ->
            val cachedPhoto = ensurePreviewCached(ip, sd, photo)
            runOnUiThread {
                val freshIndex = photos.indexOfFirst { it.dir == cachedPhoto.dir && it.jpg == cachedPhoto.jpg }
                if (openAfterFetch && freshIndex >= 0 && !cancelRequested) viewerIndex = freshIndex
            }
        }
    }

    private fun openAdjacentPhoto(delta: Int) {
        val current = viewerIndex ?: return
        val target = (current + delta).coerceIn(0, photos.lastIndex)
        if (target != current) openPhotoAt(target)
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
                    if (cancelRequested) status = "Stopped."
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
                val cached = cachedPreviewFile(sd, dirName, jpg).takeIf { it.exists() && it.length() > 0L }
                out.add(
                    PhotoItem(
                        dir = dirName,
                        jpg = jpg,
                        raw = rawByStem[jpg.substringBeforeLast(".").uppercase()],
                        cacheFile = cached,
                        takenAt = cached?.readTakenAt(),
                    )
                )
            }
        }

        return out.sortedWith(compareBy({ it.dir }, { numberNearExtension(it.jpg) }, { it.jpg }))
    }

    private fun ensurePreviewCached(ip: String, sd: String, photo: PhotoItem): PhotoItem {
        val currentIndex = photos.indexOfFirst { it.dir == photo.dir && it.jpg == photo.jpg }
        if (currentIndex >= 0) runOnUiThread { photos[currentIndex] = photos[currentIndex].copy(loading = true) }

        val file = cachedPreviewFile(sd, photo.dir, photo.jpg)
        if (!file.exists() || file.length() == 0L) {
            downloadToFile(ip, sd, photo.dir, photo.jpg, file)
        }
        val updated = photo.copy(cacheFile = file, takenAt = file.readTakenAt(), loading = false)
        val updatedIndex = photos.indexOfFirst { it.dir == updated.dir && it.jpg == updated.jpg }
        if (updatedIndex >= 0) runOnUiThread { photos[updatedIndex] = updated }
        return updated
    }

    private fun downloadToFile(ip: String, sd: String, dir: String, fileName: String, target: File) {
        if (cancelRequested) return
        val request = Request.Builder().url(photoUrl(ip, sd, dir, fileName)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $fileName")
            val body = response.body ?: error("Empty body for $fileName")
            target.parentFile?.mkdirs()
            target.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
    }

    private fun downloadSelectedRaws() {
        val targets = selected.toList()
        if (targets.isEmpty()) return
        startWork("Downloading ${targets.size} RAW file(s)...") { ip, sd ->
            var saved = 0
            var missing = 0
            targets.forEachIndexed { index, photo ->
                if (cancelRequested) return@startWork
                val raw = photo.raw
                if (raw == null) {
                    missing += 1
                    return@forEachIndexed
                }
                runOnUiThread { status = "RAW ${index + 1}/${targets.size}: $raw" }
                if (downloadRawToDownloads(ip, sd, photo.dir, raw)) saved += 1
            }
            runOnUiThread { status = "Saved $saved RAW file(s). $missing selected item(s) had no RAW listed." }
        }
    }

    private fun downloadRawToDownloads(ip: String, sd: String, dir: String, raw: String): Boolean {
        val temp = File(cacheDir, "raw/$raw.part").apply { parentFile?.mkdirs() }
        downloadToFile(ip, sd, dir, raw, temp)
        if (temp.length() < MIN_RAW_BYTES) {
            val size = temp.length()
            temp.delete()
            error("$raw downloaded only $size bytes; camera did not return a RAW file")
        }
        saveDownload(raw, temp)
        temp.delete()
        return true
    }

    private fun saveDownload(fileName: String, source: File) {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
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
        File(cacheDir, "previews").deleteRecursively()
        photos.replaceAll { it.copy(cacheFile = null, takenAt = null, loading = false) }
        selected.clear()
        viewerIndex = null
        status = "Preview cache cleared."
    }

    private fun loadExistingCache() {
        File(cacheDir, "previews").walkTopDown()
            .filter { it.isFile && it.extension.equals("JPG", ignoreCase = true) }
            .sortedBy { it.name }
            .forEach { file ->
                val parts = file.name.split("_", limit = 2)
                if (parts.size == 2) {
                    photos.add(PhotoItem(parts[0], parts[1], null, file, file.readTakenAt()))
                }
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

    private fun photoUrl(ip: String, sd: String, dir: String, fileName: String): String {
        val encodedDir = urlPart(dir)
        val encodedFile = urlPart(fileName)
        return "http://$ip/v1/photos/$encodedDir/$encodedFile?storage=sd$sd"
    }

    private fun cachedPreviewFile(sd: String, dir: String, jpg: String): File =
        File(File(cacheDir, "previews/sd$sd").apply { mkdirs() }, "${dir}_${jpg}".replace(Regex("[^A-Za-z0-9._-]"), "_"))
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
    onScan: () -> Unit,
    onPreviewAll: () -> Unit,
    onStop: () -> Unit,
    onUseCameraIp: () -> Unit,
    onClear: () -> Unit,
    onDownloadSelected: () -> Unit,
    onOpen: (PhotoItem) -> Unit,
    onCloseViewer: () -> Unit,
    onViewerPrev: () -> Unit,
    onViewerNext: () -> Unit,
    onToggleSelect: (PhotoItem) -> Unit,
    onOpenGithub: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = K1DarkColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xff101010)) {
            Box {
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
                        onDownloadSelected = onDownloadSelected,
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
            }
        }
    }
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color(0xff151515))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton("Set", onSettings)
        CompactButton(if (busy) "Stop" else "Scan", if (busy) onStop else onScan)
        CompactButton("All", onPreviewAll, enabled = !busy && canPreviewAll)
        CompactButton("RAW", onDownloadSelected, enabled = !busy && selectedCount > 0)
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
            .background(Color(0xff1a1a1a))
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
            "TL;DR: connect to Pentax Wi-Fi, Scan to list files, tap cards for just the previews you need, swipe in viewer, select keepers, then RAW saves originals to Download/k1gal.",
            color = Color(0xffd0d0d0),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            GITHUB_URL,
            color = Color(0xff9f7aea),
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
            .background(Color(0xff242424))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xff98d8ff),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(status, color = Color(0xffeeeeee), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            containerColor = if (isSelected) Color(0xff24483f) else Color(0xff1c1c1c),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            val cacheFile = photo.cacheFile
            if (cacheFile != null && cacheFile.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(cacheFile),
                    contentDescription = photo.jpg,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xff303030)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (photo.loading) "..." else "tap", color = Color(0xffbdbdbd))
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
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = photo.cardMetaText(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp).padding(bottom = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xffbdbdbd),
            style = MaterialTheme.typography.labelSmall,
        )
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
    var dragTotal = 0f
    var imageBounds by remember { mutableStateOf<Rect?>(null) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xf5000000))
                .pointerInput(photo.jpg) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null && (up.position - down.position).getDistance() < 20f) {
                            val bounds = imageBounds
                            if (bounds == null || !bounds.contains(up.position)) onClose()
                        }
                    }
                }
                .pointerInput(photo.jpg) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                        onDragEnd = {
                            if (abs(dragTotal) > 90f) {
                                if (dragTotal < 0) onNext() else onPrev()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("Close")
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val candidateWidth = maxWidth - 32.dp
                val maxImageHeight = maxHeight - 96.dp
                val widthFromHeight = maxImageHeight * 1.5f
                val imageWidth = if (candidateWidth < widthFromHeight) candidateWidth else widthFromHeight

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = rememberAsyncImagePainter(photo.cacheFile),
                        contentDescription = photo.jpg,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(imageWidth)
                            .aspectRatio(1.5f)
                            .clip(RoundedCornerShape(8.dp))
                            .onGloballyPositioned { imageBounds = it.boundsInRoot() },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("${index + 1}/$total  ${photo.displayName()}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(photo.takenAt ?: photo.dir, color = Color(0xffbdbdbd))
                }
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
                        color = Color(0xff98d8ff),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White)
                }
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
            Button(onClick = onDownloadSelected, enabled = !busy && selectedCount > 0) { Text("RAW") }
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

private fun PhotoItem.cardMetaText(): String = takenAt ?: if (raw != null) "RAW" else "JPG"

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
