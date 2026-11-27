package com.hanenashi.k1gal.camera

import com.hanenashi.k1gal.model.CameraStatus
import com.hanenashi.k1gal.model.StorageSlot
import com.hanenashi.k1gal.util.forEachObject
import com.hanenashi.k1gal.util.orEmpty
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal class K1ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val liveClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val statusClient = client.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    fun deviceStatus(ip: String): CameraStatus.Connected {
        val device = getJson("http://$ip/v1/props/device", statusClient)
        check(device.optInt("errCode") == 200) { "Camera status unavailable" }
        val slots = buildList {
            device.optJSONArray("storages").orEmpty().forEachObject { slot ->
                val name = slot.optString("name").lowercase()
                if (name == "sd1" || name == "sd2") {
                    add(
                        StorageSlot(
                            name = name.uppercase(),
                            equipped = slot.optBoolean("equipped"),
                            available = slot.optBoolean("available"),
                            writable = slot.optBoolean("writable"),
                            active = slot.optBoolean("active"),
                            remainingShots = slot.optInt("remain", -1).takeIf { it >= 0 },
                        ),
                    )
                }
            }
        }
        return CameraStatus.Connected(
            model = device.optString("model").takeIf { it.isNotBlank() },
            firmware = device.optString("firmwareVersion").takeIf { it.isNotBlank() },
            batteryPercent = device.optInt("battery", -1).takeIf { it in 0..100 },
            hot = device.optBoolean("hot"),
            slots = slots,
        )
    }

    fun cameraProperties(ip: String): JSONObject = getJson("http://$ip/v1/props/camera")

    fun lensProperties(ip: String): JSONObject = getJson("http://$ip/v1/props/lens")

    fun latestPhotoInfo(ip: String, sd: String): JSONObject =
        getJson("http://$ip/v1/photos/latest/info?storage=sd$sd")

    fun listPhotos(ip: String, sd: String): JSONObject =
        getJson("http://$ip/v1/photos?storage=sd$sd")

    fun photoInfo(ip: String, sd: String, dir: String, fileName: String): JSONObject =
        getJson("${photoPath(ip, dir, fileName)}/info?storage=sd$sd")

    fun shoot(ip: String, autofocus: String): JSONObject {
        val request = Request.Builder()
            .url("http://$ip/v1/camera/shoot")
            .post(FormBody.Builder().add("af", autofocus).build())
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Shutter HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty shutter response"))
        }
    }

    fun readPhoto(
        ip: String,
        sd: String,
        dir: String,
        fileName: String,
        size: String?,
        read: (InputStream, Long?) -> Unit,
    ) {
        val request = Request.Builder().url(photoUrl(ip, sd, dir, fileName, size)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $fileName")
            val body = response.body ?: error("Empty body for $fileName")
            read(body.byteStream(), body.contentLength().takeIf { it >= 0L })
        }
    }

    fun remoteFileSize(ip: String, sd: String, dir: String, fileName: String): Long? {
        val request = Request.Builder()
            .url(photoUrl(ip, sd, dir, fileName))
            .header("Connection", "close")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.contentLength()?.takeIf { it >= 0L }
        }
    }

    fun newLiveViewCall(ip: String): LiveViewCall =
        LiveViewCall(liveClient.newCall(Request.Builder().url("http://$ip/v1/liveview").build()))

    fun cancelCalls() = client.dispatcher.cancelAll()

    private fun getJson(url: String, http: OkHttpClient = client): JSONObject {
        val request = Request.Builder().url(url).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty response"))
        }
    }

    private fun photoPath(ip: String, dir: String, fileName: String): String =
        "http://$ip/v1/photos/${urlPart(dir)}/${urlPart(fileName)}"

    private fun photoUrl(ip: String, sd: String, dir: String, fileName: String, size: String? = null): String {
        val sizeQuery = size?.let { "size=${urlPart(it)}&" }.orEmpty()
        return "${photoPath(ip, dir, fileName)}?${sizeQuery}storage=sd$sd"
    }

    private fun urlPart(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    class LiveViewCall internal constructor(private val call: Call) {
        fun read(readStream: (InputStream) -> Unit) {
            call.execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty live-view stream")
                readStream(body.byteStream())
            }
        }

        fun cancel() = call.cancel()
    }
}
