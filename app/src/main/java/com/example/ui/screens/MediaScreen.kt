package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.model.MediaType
import com.example.core.storage.MediaAssetEntity
import com.example.ui.ImportProgressState
import com.example.ui.components.StudioFormatter
import java.io.File

@Composable
fun MediaScreen(
    mediaAssets: List<MediaAssetEntity>,
    selectedIds: Set<String>,
    importState: ImportProgressState,
    hasActiveProject: Boolean,
    onImportUris: (List<Uri>, String) -> Unit,
    onImportFolderTree: (Uri) -> Unit,
    onCancelImport: () -> Unit,
    onClearImportReport: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onDeselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onAddAssetsToProject: (List<MediaAssetEntity>) -> Unit
) {
    var filterType by remember { mutableStateOf("ALL") }
    var previewAsset by remember { mutableStateOf<MediaAssetEntity?>(null) }
    var infoAsset by remember { mutableStateOf<MediaAssetEntity?>(null) }

    // Zero-permission Photo Picker for Videos + Photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris, "Gallery")
    }

    // SAF Multi-document picker for Video, Image, and Audio files (MP4, MOV, MKV, WebM, 3GP, MP3, WAV, AAC, FLAC, OGG...)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris, "Documents")
    }

    // SAF Folder picker (Internal Storage, Downloads, DCIM, Movies, Pictures, Music, Documents)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { onImportFolderTree(it) }
    }

    val filtered = remember(mediaAssets, filterType) {
        if (filterType == "ALL") mediaAssets else mediaAssets.filter { it.mediaType == filterType }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Import Action Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Offline Media Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("media_photo_picker_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        documentPickerLauncher.launch(arrayOf("video/*", "image/*", "audio/*"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("media_files_picker_button")
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Files/Audio", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("media_folder_picker_button")
                ) {
                    Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Folder", maxLines = 1)
                }
            }

            // Filter & Selection Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "All", "VIDEO" to "Video", "IMAGE" to "Image", "AUDIO" to "Audio").forEach { (key, label) ->
                        FilterChip(
                            selected = filterType == key,
                            onClick = { filterType = key },
                            label = { Text(label) }
                        )
                    }
                }
                Row {
                    IconButton(onClick = { onSelectAll(filtered.map { it.id }) }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                    }
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = onDeselectAll) {
                            Icon(Icons.Default.Deselect, contentDescription = "Deselect All")
                        }
                    }
                }
            }
        }

        // Import Progress & Error Card
        if (importState.isImporting || importState.errorMessages.isNotEmpty() || importState.duplicateCount > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (importState.isImporting) {
                                "Importing ${importState.processedCount}/${importState.totalCount}: ${importState.currentFileName}"
                            } else {
                                "Imported ${importState.importedCount} • Duplicates skipped: ${importState.duplicateCount}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (importState.isImporting) {
                            IconButton(onClick = onCancelImport) {
                                Icon(Icons.Default.Cancel, contentDescription = "Cancel Import")
                            }
                        } else {
                            IconButton(onClick = onClearImportReport) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                    if (importState.isImporting && importState.totalCount > 0) {
                        LinearProgressIndicator(
                            progress = { importState.processedCount.toFloat() / importState.totalCount.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    importState.errorMessages.take(3).forEach { err ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Batch Selection Action Bar
        if (selectedIds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedIds.size} selected", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val chosen = mediaAssets.filter { it.id in selectedIds }
                            onAddAssetsToProject(chosen)
                        }
                    ) {
                        Icon(Icons.Default.AddToQueue, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (hasActiveProject) "Add to Timeline" else "Edit in Studio")
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onDeleteSelected) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove Selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Media Grid
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PermMedia, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text("No Media Imported Yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use Gallery, Files/Audio, or Folder above to link videos, photos, and audio locally without duplicating originals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { asset ->
                    val isSelected = asset.id in selectedIds
                    MediaAssetCard(
                        asset = asset,
                        isSelected = isSelected,
                        onClick = { onToggleSelect(asset.id) },
                        onPreview = { previewAsset = asset },
                        onInfo = { infoAsset = asset }
                    )
                }
            }
        }
    }

    previewAsset?.let { asset ->
        MediaPreviewDialog(
            asset = asset,
            onDismiss = { previewAsset = null },
            onAddToTimeline = {
                previewAsset = null
                onAddAssetsToProject(listOf(asset))
            }
        )
    }

    infoAsset?.let { asset ->
        AlertDialog(
            onDismissRequest = { infoAsset = null },
            title = { Text("Media File Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Name: ${asset.displayName}")
                    Text("Type: ${asset.mediaType} (${asset.mimeType})")
                    if (asset.width > 0) Text("Dimensions: ${asset.width} × ${asset.height}")
                    Text("Duration: ${StudioFormatter.formatDurationShort(asset.durationMs)}")
                    Text("Size: ${StudioFormatter.formatBytes(asset.sizeBytes)}")
                    Text("Source: ${asset.folderLabel}")
                    Text("URI: ${asset.uriString}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = { TextButton(onClick = { infoAsset = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun MediaAssetCard(
    asset: MediaAssetEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    onInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = asset.thumbnailPath?.let { File(it) }
                when {
                    thumbFile != null && thumbFile.exists() -> {
                        AsyncImage(
                            model = thumbFile,
                            contentDescription = asset.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    asset.mediaType == MediaType.IMAGE.name -> {
                        AsyncImage(
                            model = Uri.parse(asset.uriString),
                            contentDescription = asset.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    asset.mediaType == MediaType.AUDIO.name -> {
                        Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
                    }
                    else -> {
                        Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }

                // Selection badge
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(Color.White, CircleShape)
                    )
                }

                // Duration tag
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = if (asset.mediaType == MediaType.IMAGE.name) "IMG" else StudioFormatter.formatDurationShort(asset.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (asset.width > 0) "${asset.width}×${asset.height}" else asset.mimeType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onPreview, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (asset.mediaType == MediaType.IMAGE.name) Icons.Default.Image else Icons.Default.Movie,
                        contentDescription = "Preview",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    asset: MediaAssetEntity,
    onDismiss: () -> Unit,
    onAddToTimeline: () -> Unit
) {
    val context = LocalContext.current
    val isImage = asset.mediaType == MediaType.IMAGE.name
    val player = remember(asset.uriString) {
        if (isImage) null
        else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(asset.uriString)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(asset.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isImage) {
                    AsyncImage(
                        model = Uri.parse(asset.uriString),
                        contentDescription = asset.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAddToTimeline) { Text("Add to Timeline") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
