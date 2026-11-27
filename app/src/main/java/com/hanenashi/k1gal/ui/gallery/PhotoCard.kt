package com.hanenashi.k1gal.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.util.cardMetaText
import com.hanenashi.k1gal.util.displayName
import com.hanenashi.k1gal.util.previewRotation

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoCard(
    photo: PhotoItem,
    isSelected: Boolean,
    isRangeStart: Boolean,
    rangeAction: String?,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val name = photo.displayName()
    val selectionLabel = rangeAction?.let { "$it $name" } ?: if (isSelected) "Deselect $name" else "Select $name"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onClickLabel = if (rangeAction == null) "Open $name" else selectionLabel,
                onLongClick = onToggleSelect,
                onLongClickLabel = selectionLabel,
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected || isRangeStart) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            val thumb = photo.thumbFile?.takeIf { it.exists() }
            val cacheFile = thumb ?: photo.viewFile
            if (cacheFile != null && cacheFile.exists()) {
                val exif = if (thumb != null) photo.thumbExifOrientation else photo.viewExifOrientation
                Image(
                    painter = rememberAsyncImagePainter(cacheFile),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer(rotationZ = photo.previewRotation(exif)),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (photo.loading) "Loading…" else "Preview",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Checkbox(
                checked = isSelected || isRangeStart,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp)
                    .background(Color(0xd9191a1c), RoundedCornerShape(bottomStart = 12.dp, topEnd = 12.dp))
                    .semantics { contentDescription = selectionLabel },
            )
        }
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = photo.cardMetaText(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp).padding(bottom = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
