package com.hanenashi.k1gal.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.concurrent.thread

internal class LiveViewController(
    private val api: K1ApiClient,
    private val wifiNetwork: WifiNetworkBinder,
) {
    private class Session {
        @Volatile
        var call: K1ApiClient.LiveViewCall? = null
    }

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var activeSession: Session? = null

    // Start and stop are called on the main thread. Callbacks run there too.
    fun start(
        ip: String,
        onReady: () -> Unit,
        onFrame: (Bitmap) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        stop()
        val session = Session()
        activeSession = session
        thread(name = "k1gal-live") {
            try {
                val call = api.newLiveViewCall(ip)
                wifiNetwork.bindProcessToWifi()
                session.call = call
                if (activeSession !== session) return@thread
                call.read { stream ->
                    publish(session, onReady)
                    readMjpegFrames(stream, session) { bitmap ->
                        publish(session) { onFrame(bitmap) }
                    }
                }
            } catch (e: Exception) {
                publish(session) { onFailure(e) }
            } finally {
                session.call = null
            }
        }
    }

    fun stop() {
        val session = activeSession
        activeSession = null
        session?.call?.cancel()
    }

    private fun publish(session: Session, update: () -> Unit) {
        main.post {
            if (activeSession === session) update()
        }
    }

    private fun readMjpegFrames(input: InputStream, session: Session, onFrame: (Bitmap) -> Unit) {
        val frame = ByteArrayOutputStream(128 * 1024)
        var previous = -1
        var collecting = false
        while (activeSession === session) {
            val current = input.read()
            if (current < 0) throw EOFException("K-1 closed the live-view stream")
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
}
