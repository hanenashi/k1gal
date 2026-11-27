package com.hanenashi.k1gal.camera

import android.os.Handler
import android.os.Looper
import com.hanenashi.k1gal.util.orEmpty
import com.hanenashi.k1gal.util.toStringList
import org.json.JSONObject
import kotlin.concurrent.thread

internal class CaptureController(
    private val api: K1ApiClient,
    private val wifiNetwork: WifiNetworkBinder,
) {
    private val main = Handler(Looper.getMainLooper())
    private var busy = false

    // Call on the main thread. Status and completion callbacks run there.
    // The capture callback runs on the worker, with the shutter still locked.
    fun start(
        ip: String,
        sd: String,
        onStatus: (String) -> Unit,
        onCaptured: (JSONObject) -> Unit,
        onFinished: (Result<Unit>) -> Unit,
    ): Boolean {
        if (busy) return false
        busy = true
        onStatus("Checking camera...")
        thread(name = "k1gal-capture") {
            val result = try {
                val metadata = capture(ip, sd) { status -> main.post { onStatus(status) } }
                onCaptured(metadata)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
            main.post {
                busy = false
                onFinished(result)
            }
        }
        return true
    }

    private fun capture(ip: String, sd: String, onStatus: (String) -> Unit): JSONObject {
        wifiNetwork.bindProcessToWifi()
        val camera = api.cameraProperties(ip)
        if (camera.optString("shootMode") != "single") {
            error("Set the K-1 drive mode to single")
        }

        val lens = api.lensProperties(ip)
        val focusSettings = lens.optJSONArray("focusSettingList").orEmpty().toStringList()
        val manualFocus = lens.optString("focusMode").equals("mf", ignoreCase = true) ||
            (focusSettings.isNotEmpty() && focusSettings.all { it.equals("manual", ignoreCase = true) })
        val autofocus = if (manualFocus) "off" else "auto"

        val before = api.latestPhotoInfo(ip, sd)
        val beforeIdentity = before.photoIdentity()
        onStatus(if (manualFocus) "Capturing (manual focus)..." else "Focusing...")

        val shoot = api.shoot(ip, autofocus)
        when (shoot.optInt("errCode")) {
            200 -> Unit
            412 -> error("Camera precondition failed; check focus and drive modes")
            else -> error(shoot.optString("errMsg", "Camera rejected shutter"))
        }

        onStatus("Capturing...")
        repeat(80) {
            val latest = api.latestPhotoInfo(ip, sd)
            if (latest.optBoolean("captured") && latest.photoIdentity() != beforeIdentity) return latest
            Thread.sleep(250)
        }
        error("Timed out waiting for captured photo")
    }

    private fun JSONObject.photoIdentity(): String = "${optString("dir")}/${optString("file")}"
}
