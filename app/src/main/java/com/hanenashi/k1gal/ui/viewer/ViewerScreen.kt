package com.hanenashi.k1gal.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.util.displayName
import com.hanenashi.k1gal.util.exposureMetaText
import com.hanenashi.k1gal.util.formatMegabytes
import com.hanenashi.k1gal.util.orientationRotation
import com.hanenashi.k1gal.util.viewerTimestamp
import kotlin.math.abs

private const val PREVIEW_ASPECT_RATIO = 1.5f

@Composable
internal fun Viewer(
    photo: PhotoItem,
    index: Int,
    total: Int,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    loadingLabel: String?,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageBounds by remember { mutableStateOf<Rect?>(null) }
    var lastTapAtMs by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(photo.jpg) {
                val tapSlop = 18.dp.toPx()
                val doubleTapSlop = 80.dp.toPx()
                val swipeThreshold = 44.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedZoomed = scale > 1.05f
                    var dragTotal = Offset.Zero
                    var maxPointers = 1
                    var transformed = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        maxPointers = maxOf(maxPointers, pressed.size)
                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            transformed = true
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            val pan = change.positionChange()
                            if (scale > 1.05f || startedZoomed || transformed) {
                                if (scale > 1f) {
                                    offset += pan
                                    change.consume()
                                }
                            } else {
                                dragTotal += pan
                                if (dragTotal.getDistance() > tapSlop) change.consume()
                            }
                        }
                    }

                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }

                    val bounds = imageBounds
                    val downInsideImage = bounds?.contains(down.position) == true
                    val wasTap = maxPointers == 1 && !transformed && dragTotal.getDistance() <= tapSlop
                    if (wasTap) {
                        val isDoubleTap = downInsideImage &&
                            down.uptimeMillis - lastTapAtMs <= 320L &&
                            (down.position - lastTapPosition).getDistance() <= doubleTapSlop
                        if (isDoubleTap) {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                                offset = Offset.Zero
                            }
                            lastTapAtMs = 0L
                        } else {
                            lastTapAtMs = down.uptimeMillis
                            lastTapPosition = down.position
                            if (!downInsideImage && scale <= 1f) onClose()
                        }
                    } else if (!transformed && !startedZoomed && scale <= 1.05f) {
                        val horizontal = abs(dragTotal.x)
                        val vertical = abs(dragTotal.y)
                        if (horizontal > swipeThreshold && horizontal > vertical * 1.15f) {
                            if (dragTotal.x < 0f) onNext() else onPrev()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val portrait = photo.orientation == 6 || photo.orientation == 8
            val displayAspectRatio = if (portrait) 1f / PREVIEW_ASPECT_RATIO else PREVIEW_ASPECT_RATIO
            val viewportRatio = maxWidth / maxHeight
            val displayWidth = if (viewportRatio > displayAspectRatio) {
                maxHeight * displayAspectRatio
            } else {
                maxWidth
            }
            val displayHeight = if (viewportRatio > displayAspectRatio) {
                maxHeight
            } else {
                maxWidth / displayAspectRatio
            }
            val rawWidth = if (portrait) displayHeight else displayWidth
            val rawHeight = if (portrait) displayWidth else displayHeight

            Box(
                modifier = Modifier
                    .width(displayWidth)
                    .height(displayHeight)
                    .onGloballyPositioned { imageBounds = it.boundsInRoot() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberAsyncImagePainter(photo.viewFile),
                    contentDescription = photo.jpg,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(rawWidth)
                        .height(rawHeight)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = orientationRotation(photo.orientation),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .background(Color(0xcc160d07), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${index + 1}/$total  ${photo.displayName()}",
                color = Color(0xfffff1e3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(photo.exposureMetaText(), color = Color(0xffffb15c))
            Text(photo.viewerTimestamp(), color = Color(0xffd9a477), style = MaterialTheme.typography.bodySmall)
            photo.raw?.let { raw ->
                val size = photo.rawSizeBytes?.let(::formatMegabytes) ?: "checking size..."
                Text("$raw  $size", color = Color(0xffd9a477), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x99160d07),
                contentColor = Color(0xffffb15c),
            ),
        ) {
            Text("Close")
        }
        loadingLabel?.let { label ->
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xdd101010), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xffff8a24),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color(0xfffff1e3))
                }
            }
    }
}
