package com.hanenashi.k1gal.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hanenashi.k1gal.model.PhotoItem
import com.hanenashi.k1gal.model.key

@Composable
internal fun GalleryGrid(
    photos: List<PhotoItem>,
    selected: List<PhotoItem>,
    selectingRange: Boolean,
    rangeStart: PhotoItem?,
    probeEnabled: Boolean,
    onVisible: (List<PhotoItem>) -> Unit,
    onOpen: (PhotoItem) -> Unit,
    onToggleSelect: (PhotoItem) -> Unit,
) {
    val minimumTileWidth = if (LocalDensity.current.fontScale > 1.25f) 152.dp else 118.dp
    val gridState = rememberLazyGridState()
    val currentPhotos by rememberUpdatedState(photos)
    val visibleCallback by rememberUpdatedState(onVisible)
    LaunchedEffect(gridState, probeEnabled) {
        if (probeEnabled) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.mapNotNull { currentPhotos.getOrNull(it.index) } }
                .collect { visibleCallback(it) }
        }
    }
    val selectedKeys = selected.mapTo(hashSetOf()) { it.key }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minimumTileWidth),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(photos, key = { it.key }) { photo ->
            PhotoCard(
                photo = photo,
                isSelected = photo.key in selectedKeys,
                isRangeStart = rangeStart?.key == photo.key,
                rangeAction = if (!selectingRange) null else if (rangeStart == null) "Start range at" else "End range at",
                onOpen = { onOpen(photo) },
                onToggleSelect = { onToggleSelect(photo) },
            )
        }
    }
}
