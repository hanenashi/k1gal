package com.hanenashi.k1gal

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hanenashi.k1gal.share.ShareEvent
import com.hanenashi.k1gal.ui.GITHUB_URL
import com.hanenashi.k1gal.ui.K1GalApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.shareEvents.collect(::handleShare)
            }
        }
        setContent {
            K1GalApp(
                photos = model.galleryPhotos,
                selected = model.selectedPhotos,
                cameraIp = model.cameraIp,
                cameraStatus = model.cameraStatus,
                onRefreshCameraStatus = model::refreshCameraStatus,
                onCameraIpChange = model::updateCameraIp,
                sdCard = model.sdCard,
                onSdCardChange = model::updateSdCard,
                status = model.status,
                busy = model.busy,
                viewerIndex = model.viewerIndex,
                viewerLoadingLabel = model.viewerLoadingLabel,
                liveViewVisible = model.liveViewVisible,
                liveFrame = model.liveFrame,
                liveStatus = model.liveStatus,
                captureBusy = model.captureBusy,
                captureStatus = model.captureStatus,
                onScan = model::scanListOnly,
                onPreviewAll = model::previewAll,
                onStop = model::stopWork,
                onUseCameraIp = model::useCameraIp,
                onClear = model::clearCache,
                cacheUsage = model.cacheUsage,
                cacheLimitMb = model.cacheLimitMb,
                onCacheLimitChange = model::changeCacheLimit,
                onDownloadSelected = model::downloadSelected,
                onOpen = model::openPhoto,
                onCloseViewer = model::closeViewer,
                onViewerPrev = { model.openAdjacentPhoto(-1) },
                onViewerNext = { model.openAdjacentPhoto(1) },
                onOpenLiveView = model::openLiveView,
                onCloseLiveView = model::closeLiveView,
                onShoot = model::shootFromLiveView,
                onToggleSelect = model::toggleSelection,
                selectingRange = model.selectingRange,
                rangeStart = model.rangeStart,
                onBeginRange = model::beginRange,
                onCancelRange = model::cancelRange,
                onSelectAll = model::selectAll,
                onClearSelection = model::clearSelection,
                onPreviewPhotosVisible = model::refreshPreviewOrientations,
                onRotatePreview = model::rotatePreview,
                onSharePhoto = model::sharePhoto,
                onOpenGithub = ::openGithub,
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigation()
    }

    override fun onStop() {
        model.closeLiveView()
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

    private fun handleShare(event: ShareEvent) {
        when (event) {
            is ShareEvent.Failed -> Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            is ShareEvent.Ready -> {
                try {
                    val uri = FileProvider.getUriForFile(this, "$packageName.share", event.file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(contentResolver, event.file.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, null))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "No app is available to share this JPEG.", Toast.LENGTH_LONG).show()
                } catch (_: IllegalArgumentException) {
                    Toast.makeText(this, "Could not open the temporary JPEG.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
