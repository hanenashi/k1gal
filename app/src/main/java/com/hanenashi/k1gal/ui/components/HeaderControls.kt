package com.hanenashi.k1gal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalLayoutApi::class)
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
    val columns = if (LocalDensity.current.fontScale > 1.25f) 3 else 6
    Surface(color = Color(0xff120c08)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CompactButton("Set", "Settings", onSettings)
            CompactButton(if (busy) "Stop" else "Scan", if (busy) "Stop current operation" else "Scan camera", if (busy) onStop else onScan)
            CompactButton("All", "Cache all previews", onPreviewAll, enabled = !busy && canPreviewAll)
            CompactButton("Live", "Live view", onOpenLiveView, enabled = !busy)
            CompactButton("Down", "Download $selectedCount selected photos", onDownloadSelected, enabled = !busy && selectedCount > 0)
            CompactButton("Clear", "Clear preview cache", onClear, enabled = !busy)
        }
    }
}

@Composable
private fun RowScope.CompactButton(label: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        Text(label, modifier = Modifier.clearAndSetSemantics { }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun SelectionBar(
    selectedCount: Int,
    total: Int,
    enabled: Boolean,
    selectingRange: Boolean,
    rangeStarted: Boolean,
    onSelectAll: () -> Unit,
    onBeginRange: () -> Unit,
    onCancelRange: () -> Unit,
    onClearSelection: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                selectingRange && !rangeStarted -> "Tap the first photo"
                selectingRange -> "Tap the last photo"
                selectedCount > 0 -> "$selectedCount selected"
                else -> ""
            },
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Box {
            TextButton(
                onClick = { if (selectingRange) onCancelRange() else expanded = true },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (selectingRange) "Cancel" else "Select")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Select all") }, enabled = selectedCount < total, onClick = {
                    expanded = false
                    onSelectAll()
                })
                DropdownMenuItem(text = { Text("Select range") }, enabled = total > 1, onClick = {
                    expanded = false
                    onBeginRange()
                })
                DropdownMenuItem(text = { Text("Clear selection") }, enabled = selectedCount > 0, onClick = {
                    expanded = false
                    onClearSelection()
                })
            }
        }
    }
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
