package com.hanenashi.k1gal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hanenashi.k1gal.model.CameraStatus

private const val APP_VERSION = "0.4.0"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPanel(
    cameraIp: String,
    onCameraIpChange: (String) -> Unit,
    sdCard: String,
    onSdCardChange: (String) -> Unit,
    cameraStatus: CameraStatus,
    busy: Boolean,
    onUseCameraIp: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onOpenGithub: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = cameraIp,
                    onValueChange = onCameraIpChange,
                    label = { Text("Camera IP address") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..2).forEach { slot ->
                        FilterChip(
                            selected = sdCard == slot.toString(),
                            onClick = { onSdCardChange(slot.toString()) },
                            label = { Text("Browse SD$slot") },
                            modifier = Modifier.heightIn(min = 48.dp),
                            enabled = !busy,
                        )
                    }
                }
                TextButton(onClick = onUseCameraIp, enabled = !busy) { Text("Use detected camera IP") }
                HorizontalDivider()
                Text("Camera status", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                CameraDetails(cameraStatus)
                TextButton(onClick = onRefresh, enabled = !busy && cameraStatus != CameraStatus.Checking) {
                    Text("Check camera")
                }
                HorizontalDivider()
                TextButton(onClick = onClear, enabled = !busy) { Text("Clear preview cache") }
                TextButton(onClick = onOpenGithub) { Text("GitHub · v$APP_VERSION") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CameraDetails(status: CameraStatus) {
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (status) {
            CameraStatus.Unchecked -> Text("Status hasn’t been checked.")
            CameraStatus.Checking -> Text("Checking camera…")
            CameraStatus.Unreachable -> Text("Camera unavailable. Check the camera’s Wi-Fi and IP address.")
            is CameraStatus.Connected -> {
                Text(status.model ?: "Camera connected", style = MaterialTheme.typography.titleSmall)
                status.firmware?.let { Text("Firmware $it") }
                status.batteryPercent?.let { Text("Battery $it%") }
                if (status.hot) Text("Camera heat warning", color = MaterialTheme.colorScheme.error)
                status.slots.forEach { slot ->
                    val detail = when {
                        !slot.equipped -> "No card"
                        !slot.available -> "Unavailable"
                        !slot.writable -> "Read-only"
                        slot.remainingShots != null -> "${slot.remainingShots} shots left"
                        else -> "Ready"
                    }
                    Text("${slot.name}${if (slot.active) " · Active" else ""} · $detail")
                }
            }
        }
    }
}
