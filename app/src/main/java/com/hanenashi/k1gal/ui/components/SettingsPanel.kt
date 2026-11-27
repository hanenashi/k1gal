package com.hanenashi.k1gal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hanenashi.k1gal.ui.GITHUB_URL

private const val APP_VERSION = "0.3.0"

@Composable
internal fun SettingsPanel(
    cameraIp: String,
    onCameraIpChange: (String) -> Unit,
    sdCard: String,
    onSdCardChange: (String) -> Unit,
    busy: Boolean,
    onUseCameraIp: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xff160f0a))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("k1gal", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("v$APP_VERSION", color = Color(0xffbdbdbd), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cameraIp,
                onValueChange = onCameraIpChange,
                label = { Text("K-1 IP") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = sdCard,
                onValueChange = { if (it in listOf("1", "2", "")) onSdCardChange(it) },
                label = { Text("SD") },
                singleLine = true,
                modifier = Modifier.width(72.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseCameraIp, enabled = !busy) { Text("Use K-1 IP") }
            OutlinedButton(onClick = onOpenGithub) { Text("GitHub") }
        }
        Text(
            "TL;DR: connect to Pentax Wi-Fi, Scan to list files, tap cards for just the previews you need, swipe in viewer, select keepers, then Down saves JPEG previews or RAW originals to Download/k1gal.",
            color = Color(0xffffc08a),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            GITHUB_URL,
            color = Color(0xffff8a24),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
