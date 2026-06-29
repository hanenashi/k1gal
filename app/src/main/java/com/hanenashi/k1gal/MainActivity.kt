package com.hanenashi.k1gal

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

private const val DEFAULT_CAMERA_IP = "192.168.0.1"

data class PhotoItem(
    val dir: String,
    val jpg: String,
    val cacheFile: File,
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
    private var viewer by mutableStateOf<PhotoItem?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                viewer = viewer,
                onScan = { scanAndCache() },
                onUseCameraIp = {
                    cameraIp = guessCameraIp()
                    status = "Using likely K-1 address: $cameraIp"
                },
                onClear = { clearCache() },
                onDownloadSelected = { downloadSelectedRaws() },
                onOpen = { viewer = it },
                onCloseViewer = { viewer = null },
                onToggleSelect = { toggleSelection(it) },
            )
        }
    }

    private fun scanAndCache() {
        if (busy) return
        busy = true
        val ip = cameraIp.trim()
        val sd = sdCard.trim()
        val localIps = wifiLocalIps()
        if (localIps.contains(ip)) {
            status = "$ip is this phone. Use the K-1 gateway, usually ${guessCameraIp()}."
            busy = false
            return
        }
        status = "Binding to Wi-Fi and reading K-1 list..."

        thread(name = "k1-scan") {
            try {
                bindProcessToWifi()
                val listed = fetchPhotoList(ip, sd)
                val cacheDir = File(cacheDir, "previews/sd${sanitizeSd(sd)}").apply { mkdirs() }

                photos.clear()
                selected.clear()

                listed.forEachIndexed { index, photo ->
                    runOnUiThread { status = "Caching ${index + 1}/${listed.size}: ${photo.jpg}" }
                    val file = File(cacheDir, "${photo.dir}_${photo.jpg}".replace(Regex("[^A-Za-z0-9._-]"), "_"))
                    if (!file.exists() || file.length() == 0L) {
                        downloadToFile(ip, sd, photo.dir, photo.jpg, file)
                    }
                    runOnUiThread { photos.add(photo.copy(cacheFile = file)) }
                }

                runOnUiThread { status = "Cached ${photos.size} preview JPEGs." }
            } catch (e: Exception) {
                runOnUiThread { status = "Scan failed: ${e.message ?: "unknown error"}" }
            } finally {
                runOnUiThread { busy = false }
            }
        }
    }

    private fun fetchPhotoList(ip: String, sd: String): List<PhotoItem> {
        val json = httpText("http://$ip/v1/photos?storage=sd${sanitizeSd(sd)}")
        val root = JSONObject(json)
        val out = mutableListOf<PhotoItem>()
        root.optJSONArray("dirs").orEmpty().forEachObject { dirObj ->
            val dirName = dirObj.optString("name")
            dirObj.optJSONArray("files").orEmpty().forEachString { fileName ->
                if (fileName.endsWith(".JPG", ignoreCase = true)) {
                    out.add(PhotoItem(dirName, fileName, File("")))
                }
            }
        }
        return out.sortedWith(compareBy({ it.dir }, { numberNearExtension(it.jpg) }, { it.jpg }))
    }

    private fun downloadToFile(ip: String, sd: String, dir: String, fileName: String, target: File) {
        val url = photoUrl(ip, sd, dir, fileName)
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $fileName")
            val body = response.body ?: error("Empty body for $fileName")
            target.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
    }

    private fun downloadSelectedRaws() {
        val targets = selected.toList()
        if (targets.isEmpty() || busy) return
        busy = true
        status = "Downloading ${targets.size} RAW file(s)..."

        thread(name = "k1-raw-download") {
            var saved = 0
            try {
                bindProcessToWifi()
                targets.forEachIndexed { index, photo ->
                    runOnUiThread { status = "RAW ${index + 1}/${targets.size}: ${photo.jpg}" }
                    if (downloadRawFor(photo)) saved += 1
                }
                runOnUiThread { status = "Saved $saved/${targets.size} RAW file(s) to Download/k1gal." }
            } catch (e: Exception) {
                runOnUiThread { status = "RAW download failed: ${e.message ?: "unknown error"}" }
            } finally {
                runOnUiThread { busy = false }
            }
        }
    }

    private fun downloadRawFor(photo: PhotoItem): Boolean {
        val stem = photo.jpg.substringBeforeLast(".")
        val candidates = listOf("$stem.PEF", "$stem.DNG")
        for (candidate in candidates) {
            try {
                val url = photoUrl(cameraIp.trim(), sdCard.trim(), photo.dir, candidate)
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body ?: return@use
                    saveDownload(candidate, body.byteStream().readBytes())
                    return true
                }
            } catch (_: Exception) {
                // Try the next RAW extension.
            }
        }
        return false
    }

    private fun saveDownload(fileName: String, bytes: ByteArray) {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/k1gal")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create download entry")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write download")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun clearCache() {
        File(cacheDir, "previews").deleteRecursively()
        photos.clear()
        selected.clear()
        viewer = null
        status = "Preview cache cleared."
    }

    private fun loadExistingCache() {
        File(cacheDir, "previews").walkTopDown()
            .filter { it.isFile && it.extension.equals("JPG", ignoreCase = true) }
            .sortedBy { it.name }
            .forEach { file ->
                val parts = file.name.split("_", limit = 2)
                if (parts.size == 2) photos.add(PhotoItem(parts[0], parts[1], file))
            }
        if (photos.isNotEmpty()) status = "Loaded ${photos.size} cached previews."
    }

    private fun toggleSelection(photo: PhotoItem) {
        if (selected.contains(photo)) {
            selected.remove(photo)
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
            if (pieces.size == 4) {
                return pieces.take(3).joinToString(".") + ".1"
            }
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
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            address.hostAddress
                        } else {
                            null
                        }
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
        return "http://$ip/v1/photos/$encodedDir/$encodedFile?storage=sd${sanitizeSd(sd)}"
    }
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
    viewer: PhotoItem?,
    onScan: () -> Unit,
    onUseCameraIp: () -> Unit,
    onClear: () -> Unit,
    onDownloadSelected: () -> Unit,
    onOpen: (PhotoItem) -> Unit,
    onCloseViewer: () -> Unit,
    onToggleSelect: (PhotoItem) -> Unit,
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xfff7f7f2),
        ) {
            Box {
                Column(modifier = Modifier.fillMaxSize()) {
                    Header(
                        cameraIp = cameraIp,
                        onCameraIpChange = onCameraIpChange,
                        sdCard = sdCard,
                        onSdCardChange = onSdCardChange,
                        selectedCount = selected.size,
                        busy = busy,
                        onScan = onScan,
                        onUseCameraIp = onUseCameraIp,
                        onClear = onClear,
                        onDownloadSelected = onDownloadSelected,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xffe7e1d2))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(status, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 118.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(photos, key = { "${it.dir}/${it.jpg}" }) { photo ->
                            val isSelected = selected.contains(photo)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onOpen(photo) },
                                        onLongClick = { onToggleSelect(photo) },
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xffdcefe8) else Color.White,
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Box {
                                    Image(
                                        painter = rememberAsyncImagePainter(photo.cacheFile),
                                        contentDescription = photo.jpg,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f),
                                    )
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleSelect(photo) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .background(Color(0x99ffffff), RoundedCornerShape(bottomStart = 8.dp)),
                                    )
                                }
                                Text(
                                    text = photo.jpg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                viewer?.let { photo ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xee000000))
                            .clickable { onCloseViewer() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(photo.cacheFile),
                                contentDescription = photo.jpg,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(photo.jpg, color = Color.White)
                        }
                    }
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
    onScan: () -> Unit,
    onUseCameraIp: () -> Unit,
    onClear: () -> Unit,
    onDownloadSelected: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color(0xfffaf8f0))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "k1gal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text("$selectedCount selected", style = MaterialTheme.typography.bodyMedium)
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
            Button(onClick = onScan, enabled = !busy) {
                Text("Scan")
            }
            OutlinedButton(onClick = onUseCameraIp, enabled = !busy) {
                Text("K-1 IP")
            }
            Button(onClick = onDownloadSelected, enabled = !busy && selectedCount > 0) {
                Text("RAW")
            }
            OutlinedButton(onClick = onClear, enabled = !busy) {
                Text("Clear")
            }
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

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        optJSONObject(i)?.let(block)
    }
}

private fun JSONArray.forEachString(block: (String) -> Unit) {
    for (i in 0 until length()) {
        block(optString(i))
    }
}
