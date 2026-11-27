package com.hanenashi.k1gal.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hanenashi.k1gal.model.PhotoItem

@Composable
internal fun GalleryGrid(
    photos: List<PhotoItem>,
    selected: List<PhotoItem>,
    onOpen: (PhotoItem) -> Unit,
    onToggleSelect: (PhotoItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(photos, key = { "${it.dir}/${it.jpg}" }) { photo ->
            val isSelected = selected.any { it.dir == photo.dir && it.jpg == photo.jpg }
            PhotoCard(
                photo = photo,
                isSelected = isSelected,
                onOpen = { onOpen(photo) },
                onToggleSelect = { onToggleSelect(photo) },
            )
        }
    }
}
