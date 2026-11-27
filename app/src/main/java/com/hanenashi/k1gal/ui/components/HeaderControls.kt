package com.hanenashi.k1gal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hanenashi.k1gal.model.DownloadKind

@Composable
internal fun DownloadChoiceDialog(
    onDismiss: () -> Unit,
    onChoose: (DownloadKind) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download selected") },
        text = { Text("Save the cached camera JPEGs or download matching RAW originals.") },
        confirmButton = {
            TextButton(onClick = { onChoose(DownloadKind.RAW) }) {
                Text("RAW")
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(DownloadKind.JPEG) }) {
                Text("JPEG")
            }
        },
    )
}

@Composable
internal fun ActionBar(
    selectedCount: Int,
    busy: Boolean,
    canPreviewAll: Boolean,
    onSettings: () -> Unit,
    onScan: () -> Unit,
    onPreviewAll: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onDownloadSelected: () -> Unit,
    onOpenLiveView: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color(0xff120c08))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton("Set", onSettings)
        CompactButton(if (busy) "Stop" else "Scan", if (busy) onStop else onScan)
        CompactButton("All", onPreviewAll, enabled = !busy && canPreviewAll)
        CompactButton("Live", onOpenLiveView, enabled = !busy)
        CompactButton("Down", onDownloadSelected, enabled = !busy && selectedCount > 0)
        CompactButton("Clear", onClear, enabled = !busy)
    }
}

@Composable
private fun RowScope.CompactButton(
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
internal fun FooterStatus(
    status: String,
    busy: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xff1a1009))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xffff8a24),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(status, color = Color(0xffff9d3d), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
