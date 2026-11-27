package com.hanenashi.k1gal.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hanenashi.k1gal.model.DownloadKind
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.ui.components.ActionBar
import com.hanenashi.k1gal.ui.components.DownloadChoiceDialog
import com.hanenashi.k1gal.ui.components.FooterStatus
import com.hanenashi.k1gal.ui.components.SettingsPanel
import com.hanenashi.k1gal.ui.gallery.GalleryGrid
import com.hanenashi.k1gal.ui.live.LiveViewer
import com.hanenashi.k1gal.ui.theme.K1DarkColors
import com.hanenashi.k1gal.ui.viewer.Viewer

@Composable
internal fun K1GalApp(
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
                        GalleryGrid(photos, selected, onOpen, onToggleSelect)
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
