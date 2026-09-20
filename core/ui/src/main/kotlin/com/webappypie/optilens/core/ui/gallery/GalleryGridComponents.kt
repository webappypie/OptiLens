package com.webappypie.optilens.core.ui.gallery

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.storage.DateBucket
import com.webappypie.optilens.core.camera.storage.MediaItem
import com.webappypie.optilens.core.camera.storage.StorageStatus
import com.webappypie.optilens.core.camera.storage.ThumbnailLoader

/**
 * Thumbnail card representing a single media item in the gallery grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailCard(
    item: MediaItem,
    thumbnailLoader: ThumbnailLoader,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var thumbnailBitmap by remember(item.contentUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.contentUri) {
        thumbnailBitmap = thumbnailLoader.loadThumbnail(item.contentUri)
    }

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(1.0f)
            .clip(shape)
            .background(Color(0xFF1E1E1E))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else Modifier
            ),
    ) {
        // Thumbnail Image
        if (thumbnailBitmap != null && !thumbnailBitmap!!.isRecycled) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (item.isRaw) "RAW" else "IMG",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Top Badges Row (RAW, AI, Companion)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (item.isRaw) {
                TextBadge(
                    text = "RAW",
                    backgroundColor = Color(0xFFFFA000), // Amber
                    textColor = Color.Black,
                )
            }
            if (item.isEnhanced) {
                TextBadge(
                    text = "AI",
                    backgroundColor = Color(0xFF7C4DFF), // Purple
                    textColor = Color.White,
                )
            }
            if (item.companionUri != null) {
                TextBadge(
                    text = if (item.isRaw) "+JPG" else "+RAW",
                    backgroundColor = Color.Black.copy(alpha = 0.65f),
                    textColor = Color.White,
                )
            }
        }

        // Top End: Selection Checkbox or Favorite Indicator
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            // Favorite Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(30.dp),
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (item.isFavorite) "Favorited" else "Favorite",
                    tint = if (item.isFavorite) Color(0xFFFF4081) else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun TextBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/**
 * Section header displaying date group titles and item counts.
 */
@Composable
fun DateGroupHeader(
    bucket: DateBucket,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = bucket.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "$count ${if (count == 1) "photo" else "photos"}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Low storage and cache alert banner.
 */
@Composable
fun StorageWarningBanner(
    status: StorageStatus,
    formattedFree: String,
    onCleanCacheClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == StorageStatus.NORMAL) return

    val isCritical = status == StorageStatus.CRITICAL
    val bgColor = if (isCritical) Color(0xFFB00020) else Color(0xFFE65100)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Storage Warning",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCritical) "Storage Critically Low" else "Storage Running Low",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
                Text(
                    text = if (isCritical) {
                        "Only $formattedFree remaining. Captures are paused to prevent corruption."
                    } else {
                        "$formattedFree remaining. Cache will be cleaned automatically."
                    },
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCleanCacheClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = bgColor,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(height = 32.dp, width = 80.dp),
            ) {
                Text("Clean", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Horizontal row of filter chips (All, Favorites, AI Enhanced, RAW).
 */
@Composable
fun GalleryFilterChips(
    selectedFilter: GalleryFilter,
    onFilterSelected: (GalleryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GalleryFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
