package com.hanenashi.k1gal.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.unit.dp
import com.hanenashi.k1gal.model.CameraStatus
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun K1GalApp(
    photos: List<PhotoItem>,
    selected: List<PhotoItem>,
    cameraIp: String,
    cameraStatus: CameraStatus,
    onRefreshCameraStatus: () -> Unit,
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
    val fullscreen = viewerIndex != null || liveViewVisible
    BackHandler(enabled = fullscreen) {
        if (liveViewVisible) {
            if (!captureBusy) onCloseLiveView()
        } else {
            onCloseViewer()
        }
    }

    MaterialTheme(colorScheme = K1DarkColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().then(
                        if (fullscreen) Modifier.clearAndSetSemantics { invisibleToUser() } else Modifier,
                    ),
                ) {
                    ActionBar(
                        selectedCount = selected.size,
                        busy = busy,
                        canPreviewAll = photos.isNotEmpty(),
                        cameraStatus = cameraStatus,
                        onSettings = {
                            settingsOpen = true
                            onRefreshCameraStatus()
                        },
                        onScan = onScan,
                        onPreviewAll = onPreviewAll,
                        onStop = onStop,
                        onDownloadSelected = { downloadChoiceOpen = true },
                        onOpenLiveView = onOpenLiveView,
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (photos.isEmpty()) {
                            Column(
                                Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("No photos listed", style = MaterialTheme.typography.titleMedium)
                                Text("Tap Scan to browse the camera.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            GalleryGrid(photos, selected, onOpen, onToggleSelect)
                        }
                    }
                    FooterStatus(
                        status = status,
                        busy = busy,
                    )
                }
                if (settingsOpen) {
                    SettingsPanel(
                        cameraIp = cameraIp,
                        onCameraIpChange = onCameraIpChange,
                        sdCard = sdCard,
                        onSdCardChange = onSdCardChange,
                        cameraStatus = cameraStatus,
                        busy = busy,
                        onUseCameraIp = onUseCameraIp,
                        onRefresh = onRefreshCameraStatus,
                        onClear = {
                            onClear()
                            settingsOpen = false
                        },
                        onOpenGithub = onOpenGithub,
                        onDismiss = { settingsOpen = false },
                    )
                }
                viewerIndex?.let { index ->
                    photos.getOrNull(index)?.let { photo ->
                        Viewer(
                            photo = photo,
                            previousPhoto = photos.getOrNull(index - 1),
                            nextPhoto = photos.getOrNull(index + 1),
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
