package com.hanenashi.k1gal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hanenashi.k1gal.model.CameraStatus
import com.hanenashi.k1gal.model.DownloadKind

@Composable
internal fun DownloadChoiceDialog(onDismiss: () -> Unit, onChoose: (DownloadKind) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download selected photos") },
        text = { Text("Save full JPEGs or matching RAW originals to Download/k1gal.") },
        confirmButton = { TextButton(onClick = { onChoose(DownloadKind.RAW) }) { Text("RAW") } },
        dismissButton = { TextButton(onClick = { onChoose(DownloadKind.JPEG) }) { Text("JPEG") } },
    )
}

@Composable
internal fun ActionBar(
    selectedCount: Int,
    busy: Boolean,
    canPreviewAll: Boolean,
    cameraStatus: CameraStatus,
    onSettings: () -> Unit,
    onScan: () -> Unit,
    onPreviewAll: () -> Unit,
    onStop: () -> Unit,
    onDownloadSelected: () -> Unit,
    onOpenLiveView: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "k1gal",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        cameraStatus.summary(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Settings") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (busy) onStop else onScan,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(if (busy) "Stop" else "Scan")
                }
                OutlinedButton(
                    onClick = onOpenLiveView,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Live view")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onPreviewAll,
                    enabled = !busy && canPreviewAll,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Preview all")
                }
                TextButton(
                    onClick = onDownloadSelected,
                    enabled = !busy && selectedCount > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(if (selectedCount > 0) "Download ($selectedCount)" else "Download")
                }
            }
        }
    }
    HorizontalDivider()
}

private fun CameraStatus.summary(): String = when (this) {
    CameraStatus.Unchecked -> "Camera status not checked"
    CameraStatus.Checking -> "Checking camera…"
    CameraStatus.Unreachable -> "Camera unavailable"
    is CameraStatus.Connected -> buildList {
        add("Connected")
        batteryPercent?.let { add("$it% battery") }
        activeSlot?.remainingShots?.let { add("$it shots left") }
        if (hot) add("Heat warning")
    }.joinToString(" · ")
}

@Composable
internal fun FooterStatus(status: String, busy: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                status,
                modifier = if (busy) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
