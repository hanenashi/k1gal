package com.hanenashi.k1gal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.hanenashi.k1gal.ui.GITHUB_URL
import com.hanenashi.k1gal.ui.K1GalApp

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()
        setContent {
            K1GalApp(
                photos = model.galleryPhotos,
                selected = model.selectedPhotos,
                cameraIp = model.cameraIp,
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
                onDownloadSelected = model::downloadSelected,
                onOpen = model::openPhoto,
                onCloseViewer = model::closeViewer,
                onViewerPrev = { model.openAdjacentPhoto(-1) },
                onViewerNext = { model.openAdjacentPhoto(1) },
                onOpenLiveView = model::openLiveView,
                onCloseLiveView = model::closeLiveView,
                onShoot = model::shootFromLiveView,
                onToggleSelect = model::toggleSelection,
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
}
