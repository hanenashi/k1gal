package com.hanenashi.k1gal.ui.live

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun LiveViewer(
    frame: Bitmap?,
    status: String,
    captureBusy: Boolean,
    captureStatus: String?,
    onClose: () -> Unit,
    onShoot: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "K-1 live view",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (status.startsWith("Opening")) {
            CircularProgressIndicator(color = Color(0xffff8a24))
        }
        Text(
            captureStatus ?: status,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .background(Color(0xcc160d07), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color(0xffffb15c),
        )
        OutlinedButton(
            onClick = onClose,
            enabled = !captureBusy,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x99160d07),
                contentColor = Color(0xffffb15c),
            ),
        ) {
            Text("Close")
        }
        Button(
            onClick = onShoot,
            enabled = !captureBusy && frame != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (captureBusy) "Capturing..." else "Shutter")
        }
    }
}
