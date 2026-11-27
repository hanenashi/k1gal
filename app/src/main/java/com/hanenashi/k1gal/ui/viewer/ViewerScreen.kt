package com.hanenashi.k1gal.ui.viewer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.share.ShareQuality
import com.hanenashi.k1gal.util.displayName
import com.hanenashi.k1gal.util.exposureMetaText
import com.hanenashi.k1gal.util.formatMegabytes
import com.hanenashi.k1gal.util.previewRotation
import com.hanenashi.k1gal.util.viewerTimestamp
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val PREVIEW_ASPECT_RATIO = 1.5f

@Composable
internal fun Viewer(
    photo: PhotoItem,
    previousPhoto: PhotoItem?,
    nextPhoto: PhotoItem?,
    index: Int,
    total: Int,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    loadingLabel: String?,
    onRotatePreview: (Int?) -> Unit,
    onSharePhoto: (ShareQuality) -> Unit,
) {
    val pageKey = photo.dir to photo.jpg
    var scale by remember(pageKey) { mutableFloatStateOf(1f) }
    var offset by remember(pageKey) { mutableStateOf(Offset.Zero) }
    var imageBounds by remember { mutableStateOf<Rect?>(null) }
    var lastTapAtMs by remember(pageKey) { mutableStateOf(0L) }
    var lastTapPosition by remember(pageKey) { mutableStateOf(Offset.Zero) }
    var pageOffset by remember(pageKey) { mutableFloatStateOf(0f) }
    var settling by remember(pageKey) { mutableStateOf(false) }
    var awaitingPage by remember(pageKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canGoPrevious by rememberUpdatedState(previousPhoto != null)
    val canGoNext by rememberUpdatedState(nextPhoto != null)
    val isLoading by rememberUpdatedState(loadingLabel != null)
    val goPrevious by rememberUpdatedState(onPrev)
    val goNext by rememberUpdatedState(onNext)
    val close by rememberUpdatedState(onClose)
    var rotationMenuOpen by remember(pageKey) { mutableStateOf(false) }
    var shareDialogOpen by remember(pageKey) { mutableStateOf(false) }

    LaunchedEffect(pageKey, photo.manualRotation) {
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(pageKey, loadingLabel, awaitingPage) {
        if (awaitingPage && loadingLabel == null) {
            animate(pageOffset, 0f, animationSpec = spring(stiffness = 450f)) { value, _ ->
                pageOffset = value
            }
            awaitingPage = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .pointerInput(pageKey) {
                val tapSlop = 18.dp.toPx()
                val swipeSlop = viewConfiguration.touchSlop
                val doubleTapSlop = 80.dp.toPx()
                val flingSpeed = 650.dp.toPx()
                val minFlingDistance = 24.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    if (settling || awaitingPage || isLoading) return@awaitEachGesture
                    val startedZoomed = scale > 1.05f
                    var dragTotal = Offset.Zero
                    var maxPointers = 1
                    var transformed = false
                    var paging = false
                    val pageWidth = size.width.toFloat().coerceAtLeast(1f)
                    val swipeThreshold = minOf(pageWidth * 0.22f, 120.dp.toPx())
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)

                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.id == down.id }?.let {
                            velocity.addPosition(it.uptimeMillis, it.position)
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        maxPointers = maxOf(maxPointers, pressed.size)
                        if (pressed.size >= 2) {
                            pageOffset = 0f
                            paging = false
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
                                if (!paging) {
                                    paging = abs(dragTotal.x) > swipeSlop &&
                                        abs(dragTotal.x) > abs(dragTotal.y) * 1.15f
                                }
                                if (paging) {
                                    val atEdge = (dragTotal.x > 0 && !canGoPrevious) || (dragTotal.x < 0 && !canGoNext)
                                    pageOffset = (dragTotal.x * if (atEdge) 0.22f else 1f)
                                        .coerceIn(-pageWidth, pageWidth)
                                    change.consume()
                                }
                            }
                        }
                    }

                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }

                    val bounds = imageBounds
                    val downInsideImage = bounds?.contains(down.position) == true
                    val wasTap = maxPointers == 1 && !transformed && !paging && dragTotal.getDistance() <= tapSlop
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
                            if (!downInsideImage && scale <= 1f) close()
                        }
                    } else if (paging && !transformed) {
                        val speed = velocity.calculateVelocity().x
                        val enoughDistance = abs(dragTotal.x) >= swipeThreshold
                        val fling = abs(dragTotal.x) >= minFlingDistance && abs(speed) >= flingSpeed &&
                            speed * dragTotal.x > 0f
                        val direction = when {
                            !enoughDistance && !fling -> 0
                            dragTotal.x < 0f && canGoNext -> 1
                            dragTotal.x > 0f && canGoPrevious -> -1
                            else -> 0
                        }
                        settling = true
                        scope.launch {
                            animate(
                                initialValue = pageOffset,
                                targetValue = -direction * pageWidth,
                                animationSpec = spring(stiffness = 450f),
                            ) { value, _ -> pageOffset = value }
                            settling = false
                            if (direction != 0) {
                                awaitingPage = true
                                if (direction > 0) goNext() else goPrevious()
                            }
                        }
                    } else {
                        pageOffset = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val neighbor = if (pageOffset < 0f) nextPhoto else previousPhoto
        if (pageOffset != 0f && neighbor != null) {
            val side = if (pageOffset < 0f) 1f else -1f
            ViewerImage(
                photo = neighbor,
                modifier = Modifier.graphicsLayer { translationX = pageOffset + side * size.width },
                preview = true,
            )
        }
        ViewerImage(
            photo = photo,
            modifier = Modifier.graphicsLayer { translationX = pageOffset },
            scale = scale,
            offset = offset,
            onBounds = { imageBounds = it },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
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
            val exposure = photo.exposureMetaText()
            if (exposure.isNotBlank()) Text(exposure, color = Color(0xffffb15c))
            Text(photo.viewerTimestamp(), color = Color(0xffd9a477), style = MaterialTheme.typography.bodySmall)
            photo.raw?.let { raw ->
                val size = photo.rawSizeBytes?.let(::formatMegabytes) ?: "checking size..."
                Text("$raw  $size", color = Color(0xffd9a477), style = MaterialTheme.typography.bodySmall)
            }
            val actionsEnabled = loadingLabel == null && !settling && !awaitingPage
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    TextButton(
                        onClick = { rotationMenuOpen = true },
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Rotate") }
                    DropdownMenu(expanded = rotationMenuOpen, onDismissRequest = { rotationMenuOpen = false }) {
                        listOf("Rotate right" to 90, "Rotate left" to -90, "Use capture orientation" to null)
                            .forEach { (label, delta) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    rotationMenuOpen = false
                                    onRotatePreview(delta)
                                })
                            }
                    }
                }
                TextButton(
                    onClick = { shareDialogOpen = true },
                    enabled = actionsEnabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Share") }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val canNavigate = loadingLabel == null && !settling && !awaitingPage
            ViewerButton("Previous", onPrev, enabled = index > 0 && canNavigate)
            ViewerButton("Next", onNext, enabled = index + 1 < total && canNavigate)
            ViewerButton("Close", onClose)
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
    if (shareDialogOpen) {
        SharePhotoDialog(
            onDismiss = { shareDialogOpen = false },
            onChoose = {
                shareDialogOpen = false
                onSharePhoto(it)
            },
        )
    }
}

@Composable
private fun SharePhotoDialog(onDismiss: () -> Unit, onChoose: (ShareQuality) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onChoose(ShareQuality.PREVIEW) }) { Text("Quick preview") }
                Text("Uses the cached preview and its displayed rotation.")
                TextButton(onClick = { onChoose(ShareQuality.FULL_JPEG) }) { Text("Full JPEG") }
                Text("Fetches the original JPEG from the camera.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ViewerButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xee191a1c),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun ViewerImage(
    photo: PhotoItem,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    preview: Boolean = false,
    onBounds: ((Rect) -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val usesThumbnail = preview && photo.viewFile == null
        val file = if (usesThumbnail) photo.thumbFile else photo.viewFile
        val exif = if (usesThumbnail) photo.thumbExifOrientation else photo.viewExifOrientation
        val rotation = photo.previewRotation(exif)
        val quarterTurn = rotation == 90f || rotation == 270f
        val portrait = (exif in 5..8) xor quarterTurn
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
        val rawWidth = if (quarterTurn) displayHeight else displayWidth
        val rawHeight = if (quarterTurn) displayWidth else displayHeight

        Box(
            modifier = Modifier
                .width(displayWidth)
                .height(displayHeight)
                .onGloballyPositioned { onBounds?.invoke(it.boundsInRoot()) },
            contentAlignment = Alignment.Center,
        ) {
            if (file == null) {
                Text(
                    photo.displayName(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            } else {
                Image(
                    painter = rememberAsyncImagePainter(file),
                    contentDescription = if (preview) null else photo.jpg,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .requiredSize(rawWidth, rawHeight)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = rotation,
                        ),
                )
            }
        }
    }
}
