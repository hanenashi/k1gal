package com.hanenashi.k1gal.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.util.cardMetaText
import com.hanenashi.k1gal.util.displayName
import com.hanenashi.k1gal.util.orientationRotation

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoCard(
    photo: PhotoItem,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onToggleSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xff3a2110) else Color(0xff18120e),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            val cacheFile = photo.thumbFile ?: photo.viewFile
            if (cacheFile != null && cacheFile.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(cacheFile),
                    contentDescription = photo.jpg,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer(rotationZ = orientationRotation(photo.orientation)),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xff2a1a10)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (photo.loading) "..." else "tap", color = Color(0xffffb15c))
                }
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Text(
            text = photo.displayName(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xfffff1e3),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = photo.cardMetaText(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp).padding(bottom = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xffffb15c),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
