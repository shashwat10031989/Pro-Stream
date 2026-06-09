package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.database.MediaStream
import com.example.data.database.Playlist
import com.example.data.database.PlaylistItem
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.LocalMediaFile
import com.example.ui.viewmodel.NetworkServer
import com.example.ui.viewmodel.NetworkFile

data class PlaylistFileSource(
    val title: String,
    val urlOrUri: String,
    val isVideo: Boolean,
    val format: String,
    val durationMs: Long = 0L
)

@Composable
fun StreamDashboard(
    streams: List<MediaStream>,
    recentlyPlayed: List<MediaStream> = emptyList(),
    onStreamSelected: (MediaStream) -> Unit,
    onAddStream: (title: String, url: String, subUrl: String?, format: String) -> Unit,
    onDeleteStream: (id: Int) -> Unit,
    currentTheme: AppTheme,
    onThemeToggle: (AppTheme) -> Unit,
    scannedFiles: List<LocalMediaFile>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onSeedDemoClick: () -> Unit,
    onLocalFavoriteToggle: (Long) -> Unit,
    networkServers: List<NetworkServer> = emptyList(),
    isDiscovering: Boolean = false,
    currentServer: NetworkServer? = null,
    currentServerFiles: List<NetworkFile> = emptyList(),
    currentPath: String = "/",
    isBrowsingServer: Boolean = false,
    onDiscoverServers: () -> Unit = {},
    onAddNetworkServer: (String, String, String, Int?, String?) -> Unit = { _, _, _, _, _ -> },
    onRemoveNetworkServer: (String) -> Unit = {},
    onBrowseServer: (NetworkServer) -> Unit = {},
    onNavigateToPath: (String) -> Unit = {},
    onGoBackDirectory: () -> Unit = {},
    onDisconnectServer: () -> Unit = {},
    playlists: List<Playlist> = emptyList(),
    selectedPlaylistId: Int? = null,
    currentPlaylistItems: List<PlaylistItem> = emptyList(),
    onCreatePlaylist: (String) -> Unit = {},
    onDeletePlaylist: (Int) -> Unit = {},
    onSelectPlaylist: (Int?) -> Unit = {},
    onAddItemToPlaylist: (playlistId: Int, title: String, urlOrUri: String, isVideo: Boolean, format: String, durationMs: Long) -> Unit = { _, _, _, _, _, _ -> },
    onRemovePlaylistItem: (Int) -> Unit = {},
    onPlayPlaylistItem: (PlaylistItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Internet, 1 = Local Library, 2 = Playlists, 3 = NAS/DLNA
    var pendingPlaylistFile by remember { mutableStateOf<PlaylistFileSource?>(null) }

    // Filter streams based on search query
    val filteredStreams = remember(streams, searchQuery) {
        if (searchQuery.isBlank()) streams
        else streams.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    // Split streams into Prepopulated Samples vs Custom user items
    val sampleStreams = remember(filteredStreams) {
        filteredStreams.filter { !it.isCustom }
    }
    val customStreams = remember(filteredStreams) {
        filteredStreams.filter { it.isCustom }
    }

    // Capture the latest interrupted stream to resume (lastPositionMs > 2000 ms)
    val resumeItem = remember(streams) {
        streams.filter { it.lastPositionMs > 2000L }.maxByOrNull { it.timestamp }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header visual title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "StreamPlayer",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "VLC-Inspired Media Core & Streaming Suite",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                    )
                }

                // Actions Row: Theme Toggle Dropdown + Add Stream Link
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var themeMenuExpanded by remember { mutableStateOf(false) }

                    Box {
                        IconButton(
                            onClick = { themeMenuExpanded = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .testTag("theme_toggle_button")
                        ) {
                            val icon = when (currentTheme) {
                                AppTheme.LIGHT -> Icons.Default.LightMode
                                AppTheme.DARK -> Icons.Default.DarkMode
                                AppTheme.SYSTEM -> Icons.Default.AutoMode
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Choose App Theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = themeMenuExpanded,
                            onDismissRequest = { themeMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = "Light Theme",
                                        tint = if (currentTheme == AppTheme.LIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = { Text("Light Mode", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onThemeToggle(AppTheme.LIGHT)
                                    themeMenuExpanded = false
                                },
                                modifier = Modifier.testTag("theme_option_light")
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = "Dark Theme",
                                        tint = if (currentTheme == AppTheme.DARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = { Text("Dark Mode", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onThemeToggle(AppTheme.DARK)
                                    themeMenuExpanded = false
                                },
                                modifier = Modifier.testTag("theme_option_dark")
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoMode,
                                        contentDescription = "System Theme",
                                        tint = if (currentTheme == AppTheme.SYSTEM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = { Text("Follow System", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onThemeToggle(AppTheme.SYSTEM)
                                    themeMenuExpanded = false
                                },
                                modifier = Modifier.testTag("theme_option_system")
                            )
                        }
                    }

                    // Add link action
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_stream_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add custom stream",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Add Stream", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search filtering search bar
            if (activeTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search streams or URLs...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("stream_search_input")
                )
            }

            // Beautiful Tab Bar (Segmented Control)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Internet Feeds", "Local Gallery", "Playlists", "NAS/DLNA").forEachIndexed { index, text ->
                    val isSelected = activeTab == index
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(containerColor)
                            .clickable { activeTab = index }
                            .padding(vertical = 10.dp)
                            .testTag("gallery_tab_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val icon = when (index) {
                                0 -> Icons.Default.RssFeed
                                1 -> Icons.Default.PermMedia
                                2 -> Icons.Default.PlaylistPlay
                                else -> Icons.Default.Dns
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = text,
                                color = contentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (activeTab == 0) {
                // Resume media playback card
                AnimatedVisibility(
                    visible = resumeItem != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    resumeItem?.let { item ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onStreamSelected(item) }
                                .testTag("resume_play_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CONTINUE WATCHING",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Interrupted at ${formatTime(item.lastPositionMs)} • Speed ${item.playbackSpeed}x",
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume play",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // LazyColumn Stream list categories
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Category 1: Custom feeds
                    if (customStreams.isNotEmpty()) {
                        item {
                            Text(
                                text = "My Personal Playlist",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(customStreams, key = { it.id }) { stream ->
                            StreamItemCard(
                                stream = stream,
                                onClick = { onStreamSelected(stream) },
                                onDelete = { onDeleteStream(stream.id) },
                                onAddToPlaylist = {
                                    pendingPlaylistFile = PlaylistFileSource(
                                        title = stream.title,
                                        urlOrUri = stream.url,
                                        isVideo = stream.format == "MP4",
                                        format = stream.format
                                    )
                                }
                            )
                        }
                    }

                    // Category 2: Standard/Built-in test feeds (HLS/DASH protocols)
                    if (sampleStreams.isNotEmpty()) {
                        item {
                            Text(
                                text = "Sample Live Streams (HLS & DASH)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(sampleStreams, key = { it.id }) { stream ->
                            StreamItemCard(
                                stream = stream,
                                onClick = { onStreamSelected(stream) },
                                onDelete = null,
                                onAddToPlaylist = {
                                    pendingPlaylistFile = PlaylistFileSource(
                                        title = stream.title,
                                        urlOrUri = stream.url,
                                        isVideo = stream.format == "MP4",
                                        format = stream.format
                                    )
                                }
                            )
                        }
                    }

                    // Empty State handler
                    if (customStreams.isEmpty() && sampleStreams.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.TvOff,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No streams matched your search.",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (activeTab == 1) {
                LocalMediaGallery(
                    scannedFiles = scannedFiles,
                    recentlyPlayed = recentlyPlayed,
                    isScanning = isScanning,
                    onScanClick = onScanClick,
                    onSeedDemoClick = onSeedDemoClick,
                    onFavoriteToggle = onLocalFavoriteToggle,
                    searchQuery = searchQuery,
                    onFileSelected = { file ->
                        val mappedStream = MediaStream(
                            id = file.id.toInt().coerceAtMost(-1000).coerceAtLeast(-10000),
                            title = file.title,
                            url = file.uriString,
                            isCustom = true,
                            format = if (file.isVideo) "MP4" else "MP3"
                        )
                        onStreamSelected(mappedStream)
                    },
                    onStreamSelected = onStreamSelected,
                    onAddToPlaylistClick = { file ->
                        pendingPlaylistFile = PlaylistFileSource(
                            title = file.title,
                            urlOrUri = file.uriString,
                            isVideo = file.isVideo,
                            format = if (file.isVideo) "MP4" else "MP3",
                            durationMs = file.durationMs
                        )
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else if (activeTab == 2) {
                PlaylistsGallery(
                    playlists = playlists,
                    selectedPlaylistId = selectedPlaylistId,
                    currentPlaylistItems = currentPlaylistItems,
                    onCreatePlaylist = onCreatePlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onSelectPlaylist = onSelectPlaylist,
                    onRemovePlaylistItem = onRemovePlaylistItem,
                    onPlayPlaylistItem = onPlayPlaylistItem,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                NetworkServersGallery(
                    servers = networkServers,
                    isDiscovering = isDiscovering,
                    currentServer = currentServer,
                    currentServerFiles = currentServerFiles,
                    currentPath = currentPath,
                    isBrowsing = isBrowsingServer,
                    onDiscoverClick = onDiscoverServers,
                    onAddServerClick = onAddNetworkServer,
                    onRemoveServer = onRemoveNetworkServer,
                    onServerClick = onBrowseServer,
                    onPathClick = onNavigateToPath,
                    onBackClick = onGoBackDirectory,
                    onDisconnectClick = onDisconnectServer,
                    searchQuery = searchQuery,
                    onFileSelected = { file ->
                        val mappedStream = MediaStream(
                            id = -20000 - file.name.hashCode(),
                            title = file.name,
                            url = file.url,
                            isCustom = true,
                            format = if (file.isVideo) "MP4" else "MP3"
                        )
                        onStreamSelected(mappedStream)
                    },
                    onAddToPlaylistClick = { file ->
                        pendingPlaylistFile = PlaylistFileSource(
                            title = file.name,
                            urlOrUri = file.url,
                            isVideo = file.isVideo,
                            format = if (file.isVideo) "MP4" else "MP3"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }

    // Modal dialog for adding custom feeds
    if (showAddDialog) {
        AddStreamDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url, subUrl, format ->
                onAddStream(title, url, subUrl, format)
                showAddDialog = false
            }
        )
    }

    // Modal dialog for inserting file to custom playlists
    pendingPlaylistFile?.let { file ->
        AddToPlaylistDialog(
            fileTitle = file.title,
            fileUrlOrUri = file.urlOrUri,
            isVideo = file.isVideo,
            format = file.format,
            durationMs = file.durationMs,
            playlists = playlists,
            onCreatePlaylist = onCreatePlaylist,
            onAddItemToPlaylist = onAddItemToPlaylist,
            onDismissRequest = { pendingPlaylistFile = null }
        )
    }
}

// Single Stream Row Card Representing VLC File/Stream Item
@Composable
fun StreamItemCard(
    stream: MediaStream,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("stream_card_${stream.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Status Protocol Icon Block
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                val (icon, color) = when (stream.format) {
                    "HLS" -> Pair(Icons.Default.LiveTv, MaterialTheme.colorScheme.primary)
                    "DASH" -> Pair(Icons.Default.Tv, Color(0xFF00BFFF))
                    "MP4" -> Pair(Icons.Default.Videocam, Color(0xFF2ECC71))
                    else -> Pair(Icons.Default.MovieFilter, Color(0xFFF1C40F))
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Central Info Columns
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stream.url,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom badge items row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Protocol badge label
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stream.format,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Caption status tag
                    if (!stream.subtitleUrl.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0x332ECC71),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = null,
                                    tint = Color(0xFF2ECC71),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "SUB",
                                    color = Color(0xFF2ECC71),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Resume progress status
                    if (stream.lastPositionMs > 2000L) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color.DarkGray.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Resuming: ${formatTime(stream.lastPositionMs)}",
                                color = Color.LightGray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Right-side triggers: Delete / Play Indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onAddToPlaylist != null) {
                    IconButton(
                        onClick = onAddToPlaylist,
                        modifier = Modifier.testTag("add_to_playlist_stream_${stream.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Add stream to playlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_stream_${stream.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Stream link",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// Custom Modal dialogue for user addition of links
@Composable
fun AddStreamDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, subUrl: String?, format: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("Auto") }
    var urlError by remember { mutableStateOf<String?>(null) }

    val formats = listOf("Auto", "HLS", "DASH", "MP4")
    var expandedDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Custom Network Feed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Divider()

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Stream Title (e.g. My Live Camera)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_stream_title_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        if (it.isNotEmpty()) urlError = null
                    },
                    label = { Text("Stream URL (.m3u8, .mpd, .mp4, etc.)") },
                    singleLine = true,
                    isError = urlError != null,
                    supportingText = {
                        if (urlError != null) {
                            Text(text = urlError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_stream_url_input")
                )

                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    label = { Text("External Subtitle URL (.vtt, .srt) (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_stream_sub_input")
                )

                // Dropdown layout selecting protocol type manually
                Box {
                    OutlinedButton(
                        onClick = { expandedDropdown = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("format_dropdown_trigger")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Codec Format: $format", color = MaterialTheme.colorScheme.onSurface)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    ) {
                        formats.forEach { form ->
                            DropdownMenuItem(
                                text = { Text(text = form, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    format = form
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (url.isBlank()) {
                                urlError = "URL address cannot be blank!"
                            } else {
                                onConfirm(title.trim(), url.trim(), subUrl.trim(), format)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("confirm_add_stream_button")
                    ) {
                        Text("Add Feed")
                    }
                }
            }
        }
    }
}

// Formats millisecond positions to MM:SS style
private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 65
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun LocalMediaGallery(
    scannedFiles: List<LocalMediaFile>,
    recentlyPlayed: List<MediaStream> = emptyList(),
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onSeedDemoClick: () -> Unit,
    onFileSelected: (LocalMediaFile) -> Unit,
    onStreamSelected: (MediaStream) -> Unit = {},
    onFavoriteToggle: (Long) -> Unit,
    onAddToPlaylistClick: (LocalMediaFile) -> Unit = {},
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Check permission
    val isPermissionGranted = remember { mutableStateOf(false) }
    
    fun updatePermissionStatus() {
        isPermissionGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            hasVideo && hasAudio
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Read current status
    LaunchedEffect(Unit) {
        updatePermissionStatus()
        // Auto scan if permission already granted and list empty
        if (isPermissionGranted.value && scannedFiles.isEmpty()) {
            onScanClick()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        isPermissionGranted.value = granted
        if (granted) {
            onScanClick()
        }
    }

    var selectedCategory by remember { mutableStateOf(0) } // 0 = Videos, 1 = Audio, 2 = Favorites
    var localSearchQuery by remember { mutableStateOf("") }

    // Filter list based on localSearchQuery local filtering (filename & metadata) & category tabs
    val filteredFiles = remember(scannedFiles, localSearchQuery, selectedCategory) {
        val catFiltered = when (selectedCategory) {
            0 -> scannedFiles.filter { it.isVideo }
            1 -> scannedFiles.filter { !it.isVideo }
            else -> scannedFiles.filter { it.isFavorite }
        }
        if (localSearchQuery.isBlank()) {
            catFiltered
        } else {
            val trimmedQuery = localSearchQuery.trim().lowercase()
            
            // Extract size operators (e.g., >10mb, <5mb, >100kb, etc.)
            val sizeRegex = """([<>])\s*(\d+(?:\.\d+)?)\s*(mb|kb|gb|b)?""".toRegex()
            val sizeMatch = sizeRegex.find(trimmedQuery)
            
            // Extract duration operators (e.g., >2m, <30s, etc.)
            val durationRegex = """([<>])\s*(\d+)\s*(m|min|s|sec)""".toRegex()
            val durationMatch = durationRegex.find(trimmedQuery)
            
            // Helper strings to clean search terms (removing the metadata filters)
            var cleanQuery = trimmedQuery
            if (sizeMatch != null) {
                cleanQuery = cleanQuery.replace(sizeMatch.value, "").trim()
            }
            if (durationMatch != null) {
                cleanQuery = cleanQuery.replace(durationMatch.value, "").trim()
            }
            
            catFiltered.filter { file ->
                var matches = true
                
                // 1. Size Metadata Filter
                if (sizeMatch != null) {
                    val operator = sizeMatch.groupValues[1]
                    val value = sizeMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                    val unit = sizeMatch.groupValues[3].ifEmpty { "mb" }
                    val factor = when (unit) {
                        "kb" -> 1024.0
                        "mb" -> 1024.0 * 1024.0
                        "gb" -> 1024.0 * 1024.0 * 1024.0
                        else -> 1.0
                    }
                    val queryBytes = (value * factor).toLong()
                    matches = matches && if (operator == ">") file.sizeBytes > queryBytes else file.sizeBytes < queryBytes
                }
                
                // 2. Duration Metadata Filter
                if (durationMatch != null) {
                    val operator = durationMatch.groupValues[1]
                    val value = durationMatch.groupValues[2].toLongOrNull() ?: 0L
                    val unit = durationMatch.groupValues[3]
                    val factor = when (unit) {
                        "m", "min" -> 60_000L
                        else -> 1000L
                    }
                    val queryMs = value * factor
                    matches = matches && if (operator == ">") file.durationMs > queryMs else file.durationMs < queryMs
                }
                
                // 3. Filename or standard metadata (extension, type, favorite) match
                if (cleanQuery.isNotEmpty()) {
                    val nameMimeMatch = file.title.contains(cleanQuery, ignoreCase = true) ||
                            (file.mimeType?.contains(cleanQuery, ignoreCase = true) == true) ||
                            file.uriString.contains(cleanQuery, ignoreCase = true)
                            
                    val specialMatch = when (cleanQuery) {
                        "video", "videos" -> file.isVideo
                        "audio", "music", "sound", "mp3" -> !file.isVideo
                        "favorite", "favorites", "fave", "fav" -> file.isFavorite
                        "mp4" -> file.uriString.endsWith(".mp4", ignoreCase = true) || (file.mimeType?.contains("mp4", ignoreCase = true) == true)
                        "mkv" -> file.uriString.endsWith(".mkv", ignoreCase = true) || (file.mimeType?.contains("matroska", ignoreCase = true) == true)
                        "aac" -> file.uriString.endsWith(".aac", ignoreCase = true) || (file.mimeType?.contains("aac", ignoreCase = true) == true)
                        "flac" -> file.uriString.endsWith(".flac", ignoreCase = true) || (file.mimeType?.contains("flac", ignoreCase = true) == true)
                        else -> false
                    }
                    matches = matches && (nameMimeMatch || specialMatch)
                }
                
                matches
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("local_gallery_container")
    ) {
        if (!isPermissionGranted.value && scannedFiles.isEmpty()) {
            // Permission Solicitation UI Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "Access Local Media Files",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "To find, catalog and play audio and video files saved locally on your device, authorize storage access below.",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(perms)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_permission_button")
                    ) {
                        Text("Grant Storage Access")
                    }

                    TextButton(
                        onClick = onSeedDemoClick,
                        modifier = Modifier.testTag("load_demo_media_button")
                    ) {
                        Text("Or, load simulator mock files instead")
                    }
                }
            }
        } else {
            // Main list of scanned elements with control bar
            Column(modifier = Modifier.fillMaxSize()) {
                // Media Summary & Scanner Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "My Local Storage",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (filteredFiles.isEmpty()) "0 files found" else "${filteredFiles.size} media files",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Rescan option Button
                        OutlinedButton(
                            onClick = {
                                if (isPermissionGranted.value) {
                                    onScanClick()
                                } else {
                                    onScanClick()
                                }
                            },
                            enabled = !isScanning,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("rescan_files_button")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scanning...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh scan",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Storage", fontSize = 12.sp)
                            }
                        }

                        // Seeder option Button if list is currently empty
                        if (scannedFiles.isEmpty()) {
                            Button(
                                onClick = onSeedDemoClick,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("seed_scanned_files_button")
                            ) {
                                Text("Load Demos", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Recently Played Section (Dynamic horizontal checklist/scroll)
                if (recentlyPlayed.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recently Played",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("recently_played_list_row"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            recentlyPlayed.forEach { stream ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clickable { onStreamSelected(stream) }
                                        .testTag("recently_played_item_${stream.id}")
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val (icon, color) = when (stream.format) {
                                                "MP4" -> Pair(Icons.Default.Videocam, Color(0xFF2ECC71))
                                                "MP3" -> Pair(Icons.Default.MusicNote, Color(0xFFE74C3C))
                                                "HLS" -> Pair(Icons.Default.LiveTv, MaterialTheme.colorScheme.primary)
                                                "DASH" -> Pair(Icons.Default.Tv, Color(0xFF00BFFF))
                                                else -> Pair(Icons.Default.MovieFilter, Color(0xFFF1C40F))
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = color,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stream.format,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.LightGray,
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stream.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (stream.lastPositionMs > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Resumes at ${formatTime(stream.lastPositionMs)}",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Search Bar with Metadata Suggestion Chips
                OutlinedTextField(
                    value = localSearchQuery,
                    onValueChange = { localSearchQuery = it },
                    placeholder = { Text("Search by name, size or extension...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (localSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { localSearchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("local_gallery_search_input")
                )

                // Quick Metadata Chips Row (Scrollable horizontally)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chips = listOf(
                        "mp4" to "mp4",
                        "mp3" to "mp3",
                        "mkv" to "mkv",
                        "> 10MB" to ">10mb",
                        "< 5MB" to "<5mb",
                        "Favorites" to "fave",
                        "Video files" to "video",
                        "Audio files" to "audio"
                    )
                    chips.forEach { (label, queryValue) ->
                        val isQueryActive = localSearchQuery.contains(queryValue, ignoreCase = true)
                        
                        FilterChip(
                            selected = isQueryActive,
                            onClick = {
                                if (isQueryActive) {
                                    localSearchQuery = localSearchQuery.replace(queryValue, "").replace("\\s+".toRegex(), " ").trim()
                                } else {
                                    localSearchQuery = if (localSearchQuery.isBlank()) queryValue else "$localSearchQuery $queryValue"
                                }
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            modifier = Modifier.testTag("local_metadata_chip_$queryValue")
                        )
                    }
                }

                // Categorization tab-bar 'Videos', 'Audio', 'Favorites'
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Videos", "Audio", "Favorites").forEachIndexed { index, title ->
                        val isSelected = selectedCategory == index
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(containerColor)
                                .clickable { selectedCategory = index }
                                .padding(vertical = 8.dp)
                                .testTag("local_category_tab_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val icon = when (index) {
                                    0 -> Icons.Default.Movie
                                    1 -> Icons.Default.MusicNote
                                    else -> Icons.Default.Favorite
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    color = contentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedCategory == 2) Icons.Default.FavoriteBorder else Icons.Default.AppShortcut,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = when (selectedCategory) {
                                    0 -> "No local videos found."
                                    1 -> "No local audio files found."
                                    else -> "No local favorites added yet."
                                },
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            if (scannedFiles.isEmpty()) {
                                Text(
                                    text = "Tap 'Load Demos' or 'Scan' to populate.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    // Scrollable Gallery Grid component
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            LocalMediaCard(
                                file = file,
                                onClick = { onFileSelected(file) },
                                onFavoriteToggle = { onFavoriteToggle(file.id) },
                                onAddToPlaylist = { onAddToPlaylistClick(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalMediaCard(
    file: LocalMediaFile,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("local_file_card_${file.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Media Category Thumbnail Banner representation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = if (file.isVideo) {
                                listOf(Color(0xFF2C3E50), Color(0xFF0F2027))
                            } else {
                                listOf(Color(0xFFD35400), Color(0xFF2C3E50))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Play overlap circular overlay
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (file.isVideo) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                        contentDescription = "Play file",
                        tint = if (file.isVideo) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Symmetrical Playlist Add trigger on the top-left corner of the thumbnail
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .testTag("playlist_add_${file.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Small absolute badge in top-right corner for favorites toggle
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .testTag("favorite_toggle_${file.id}")
                ) {
                    Icon(
                        imageVector = if (file.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (file.isFavorite) Color(0xFFFF2D55) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Small absolute badge in bottom-right corner for type
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (file.isVideo) "VIDEO" else "AUDIO",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom metadata container info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = file.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Duration badge text label
                    Text(
                        text = formatDuration(file.durationMs),
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )

                    // Size label helper
                    Text(
                        text = formatBytes(file.sizeBytes),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// Helpers inside the exact same file scope to avoid conflicts
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val secs = totalSecs % 60
    val mins = (totalSecs / 60) % 60
    val hrs = totalSecs / 3600
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val groupIndex = digitGroups.coerceIn(0, units.size - 1)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, groupIndex.toDouble()), units[groupIndex])
}

@Composable
fun NetworkServersGallery(
    servers: List<NetworkServer>,
    isDiscovering: Boolean,
    currentServer: NetworkServer?,
    currentServerFiles: List<NetworkFile>,
    currentPath: String,
    isBrowsing: Boolean,
    onDiscoverClick: () -> Unit,
    onAddServerClick: (String, String, String, Int?, String?) -> Unit,
    onRemoveServer: (String) -> Unit,
    onServerClick: (NetworkServer) -> Unit,
    onPathClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    searchQuery: String,
    onFileSelected: (NetworkFile) -> Unit,
    onAddToPlaylistClick: ((NetworkFile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var nasSearchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("nas_gallery_container")
    ) {
        if (!isBrowsing) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header & Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Network Stores (NAS / DLNA)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${servers.size} configured servers",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDiscoverClick,
                            enabled = !isDiscovering,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("discover_servers_button")
                        ) {
                            if (isDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scanning...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Discover servers",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discover", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("show_add_nas_server_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Server",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add NAS", fontSize = 12.sp)
                        }
                    }
                }

                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "No network storage servers connected.",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tap 'Discover' to scan your local Wi-Fi subnet or 'Add NAS' to configure SMB/DLNA shares.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = nasSearchQuery,
                        onValueChange = { nasSearchQuery = it },
                        placeholder = { Text("Search servers by name, host or protocol...", color = Color.Gray) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        },
                        trailingIcon = {
                            if (nasSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { nasSearchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.DarkGray,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("nas_servers_list_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val protocols = listOf("SMB", "DLNA", "WebDAV", "FTP")
                        protocols.forEach { proto ->
                            val isQueryActive = nasSearchQuery.contains(proto, ignoreCase = true)
                            FilterChip(
                                selected = isQueryActive,
                                onClick = {
                                    if (isQueryActive) {
                                        nasSearchQuery = nasSearchQuery.replace(proto, "").replace("\\s+".toRegex(), " ").trim()
                                    } else {
                                        nasSearchQuery = if (nasSearchQuery.isBlank()) proto else "$nasSearchQuery $proto"
                                    }
                                },
                                label = { Text(proto, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                modifier = Modifier.testTag("nas_server_proto_chip_$proto")
                            )
                        }
                    }

                    val filteredServers = remember(servers, nasSearchQuery) {
                        if (nasSearchQuery.isBlank()) servers
                        else {
                            val lowerQuery = nasSearchQuery.trim().lowercase()
                            servers.filter { server ->
                                server.name.contains(lowerQuery, ignoreCase = true) ||
                                server.host.contains(lowerQuery, ignoreCase = true) ||
                                server.protocol.lowercase().contains(lowerQuery)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredServers, key = { it.id }) { server ->
                            ServerCard(
                                server = server,
                                onClick = { onServerClick(server) },
                                onRemove = { onRemoveServer(server.id) }
                            )
                        }
                    }
                }
            }
        } else {
            // Direct Browse Files Mode
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar inside server browse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("nas_back_directory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Go back folder"
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = currentServer?.name ?: "Remote Host Connection",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Path: $currentPath",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDisconnectClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("disconnect_nas_button")
                    ) {
                        Text("Disconnect", fontSize = 11.sp)
                    }
                }

                // Search Bar with Metadata Suggestion Chips for files in server browsing
                OutlinedTextField(
                    value = nasSearchQuery,
                    onValueChange = { nasSearchQuery = it },
                    placeholder = { Text("Search remote files by name, size, or format...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (nasSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { nasSearchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("nas_files_search_input")
                )

                // Quick Metadata Chips Row (Scrollable horizontally)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fileChips = listOf(
                        "mp4" to "mp4",
                        "mp3" to "mp3",
                        "mkv" to "mkv",
                        "> 10MB" to ">10mb",
                        "< 5MB" to "<5mb",
                        "Folders" to "folder",
                        "Videos" to "video",
                        "Audio" to "audio"
                    )
                    fileChips.forEach { (label, queryValue) ->
                        val isQueryActive = nasSearchQuery.contains(queryValue, ignoreCase = true)
                        FilterChip(
                            selected = isQueryActive,
                            onClick = {
                                if (isQueryActive) {
                                    nasSearchQuery = nasSearchQuery.replace(queryValue, "").replace("\\s+".toRegex(), " ").trim()
                                } else {
                                    nasSearchQuery = if (nasSearchQuery.isBlank()) queryValue else "$nasSearchQuery $queryValue"
                                }
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            modifier = Modifier.testTag("nas_file_metadata_chip_$queryValue")
                        )
                    }
                }

                // Filter local results inside folders based on search query
                val filteredFiles = remember(currentServerFiles, nasSearchQuery) {
                    if (nasSearchQuery.isBlank()) currentServerFiles
                    else {
                        val trimmedQuery = nasSearchQuery.trim().lowercase()
                        
                        // Extract size operators (e.g., >10mb, <5mb, etc.)
                        val sizeRegex = """([<>])\s*(\d+(?:\.\d+)?)\s*(mb|kb|gb|b)?""".toRegex()
                        val sizeMatch = sizeRegex.find(trimmedQuery)
                        
                        val isVideoQuery = trimmedQuery == "video" || trimmedQuery == "movies" || trimmedQuery == "movie"
                        val isAudioQuery = trimmedQuery == "audio" || trimmedQuery == "music" || trimmedQuery == "song"
                        val isDirQuery = trimmedQuery == "folder" || trimmedQuery == "directory" || trimmedQuery == "dir"
                        
                        var cleanQuery = trimmedQuery
                        if (sizeMatch != null) {
                            cleanQuery = cleanQuery.replace(sizeMatch.value, "").trim()
                        }
                        
                        currentServerFiles.filter { file ->
                            var matches = true
                            
                            // Size constraint
                            if (sizeMatch != null && file.sizeBytes != null) {
                                val operator = sizeMatch.groupValues[1]
                                val value = sizeMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                                val unit = sizeMatch.groupValues[3].ifEmpty { "mb" }
                                val factor = when (unit) {
                                    "kb" -> 1024.0
                                    "mb" -> 1024.0 * 1024.0
                                    "gb" -> 1024.0 * 1024.0 * 1024.0
                                    else -> 1.0
                                }
                                val queryBytes = (value * factor).toLong()
                                matches = matches && if (operator == ">") file.sizeBytes > queryBytes else file.sizeBytes < queryBytes
                            }
                            
                            if (cleanQuery.isNotEmpty()) {
                                val nameMatch = file.name.contains(cleanQuery, ignoreCase = true) ||
                                        file.path.contains(cleanQuery, ignoreCase = true)
                                        
                                val specialMatch = when (cleanQuery) {
                                    "video", "videos" -> file.isVideo && !file.isDirectory
                                    "audio", "music", "song", "mp3" -> !file.isVideo && !file.isDirectory
                                    "folder", "directory", "dir" -> file.isDirectory
                                    "mp4" -> file.name.endsWith(".mp4", ignoreCase = true)
                                    "mkv" -> file.name.endsWith(".mkv", ignoreCase = true)
                                    "aac" -> file.name.endsWith(".aac", ignoreCase = true)
                                    "flac" -> file.name.endsWith(".flac", ignoreCase = true)
                                    else -> false
                                }
                                matches = matches && (nameMatch || specialMatch)
                            }
                            
                            matches
                        }
                    }
                }

                if (currentServerFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Accessing remote NAS shared folder...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "No items match query in this directory.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredFiles) { file ->
                            NetworkFileListItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        onPathClick(file.path)
                                    } else {
                                        onFileSelected(file)
                                    }
                                },
                                onAddToPlaylist = onAddToPlaylistClick?.let { { it(file) } }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNasServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, proto, host, port, path ->
                onAddServerClick(name, proto, host, port, path)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ServerCard(
    server: NetworkServer,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("server_card_${server.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (server.protocol) {
                            "DLNA" -> Icons.Default.Router
                            "SMB" -> Icons.Default.Storage
                            "WebDAV" -> Icons.Default.Language
                            else -> Icons.Default.Dns
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = server.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${server.protocol} • ${server.host}${server.port?.let { ":$it" } ?: ""}",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(
                            when (server.protocol) {
                                "DLNA" -> Color(0xFF2ECC71).copy(alpha = 0.2f)
                                "SMB" -> Color(0xFF3498DB).copy(alpha = 0.2f)
                                "WebDAV" -> Color(0xFF9B59B6).copy(alpha = 0.2f)
                                else -> Color(0xFFF1C40F).copy(alpha = 0.2f)
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = server.protocol,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (server.protocol) {
                            "DLNA" -> Color(0xFF2ECC71)
                            "SMB" -> Color(0xFF3498DB)
                            "WebDAV" -> Color(0xFF9B59B6)
                            else -> Color(0xFFF1C40F)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("delete_server_button_${server.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Server",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkFileListItem(
    file: NetworkFile,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("nas_file_item_${file.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (file.isDirectory) {
                        Icons.Default.Folder
                    } else if (file.isVideo) {
                        Icons.Default.PlayCircle
                    } else {
                        Icons.Default.MusicNote
                    },
                    contentDescription = null,
                    tint = if (file.isDirectory) {
                        Color(0xFFFFB300)
                    } else if (file.isVideo) {
                        Color(0xFF2ECC71)
                    } else {
                        Color(0xFFE74C3C)
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = file.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!file.isDirectory) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (file.sizeBytes != null) formatBytes(file.sizeBytes) else "Streamable Remote URL",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            if (file.isDirectory) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onAddToPlaylist != null) {
                        IconButton(
                            onClick = onAddToPlaylist,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("nas_add_to_playlist_${file.name}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Add remote file to playlist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Stream now",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddNasServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, protocol: String, host: String, port: Int?, path: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("SMB") } // Default to SMB
    var host by remember { mutableStateOf("") }
    var portString by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }

    val protocols = listOf("SMB", "DLNA", "WebDAV", "FTP")

    // Automatically set default port when protocol changes to ease UX
    LaunchedEffect(protocol) {
        if (portString.isBlank() || portString == "445" || portString == "50001" || portString == "80" || portString == "21") {
            portString = when (protocol) {
                "SMB" -> "445"
                "DLNA" -> "50001"
                "WebDAV" -> "80"
                "FTP" -> "21"
                else -> ""
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Network Server (NAS)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Select Network Streaming Protocol",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    protocols.forEach { proto ->
                        val isSelected = protocol == proto
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { protocol = proto }
                                .padding(vertical = 6.dp)
                                .testTag("proto_select_$proto"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = proto,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Friendly Name") },
                    placeholder = { Text("e.g. Living Room NAS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("nas_input_name")
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host IP / Server Domain") },
                    placeholder = { Text("e.g. 192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("nas_input_host")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = portString,
                        onValueChange = { portString = it },
                        label = { Text("Port (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("nas_input_port")
                    )

                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Share / Path Name") },
                        placeholder = { Text("e.g. movies") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f).testTag("nas_input_path")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("nas_cancel_button")
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (host.isNotBlank()) {
                                onConfirm(
                                    name,
                                    protocol,
                                    host,
                                    portString.toIntOrNull(),
                                    path
                                )
                            }
                        },
                        enabled = host.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("nas_save_button")
                    ) {
                        Text("Save & Connect")
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    fileTitle: String,
    fileUrlOrUri: String,
    isVideo: Boolean,
    format: String,
    durationMs: Long,
    playlists: List<Playlist>,
    onCreatePlaylist: (String) -> Unit,
    onAddItemToPlaylist: (playlistId: Int, title: String, urlOrUri: String, isVideo: Boolean, format: String, durationMs: Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var showCreateForm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_to_playlist_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add to Playlist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "File: $fileTitle",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                if (playlists.isEmpty() && !showCreateForm) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "You don't have any playlists yet.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                } else if (!showCreateForm) {
                    Text(
                        text = "Select a playlist:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable {
                                        onAddItemToPlaylist(
                                            playlist.id,
                                            fileTitle,
                                            fileUrlOrUri,
                                            isVideo,
                                            format,
                                            durationMs
                                        )
                                        onDismissRequest()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = playlist.name,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (showCreateForm) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Playlist Name") },
                            placeholder = { Text("e.g. My Favorites") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_playlist_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCreateForm = false }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        onCreatePlaylist(newPlaylistName.trim())
                                        newPlaylistName = ""
                                        showCreateForm = false
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Create")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showCreateForm = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("show_create_playlist_form_button"),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Playlist", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsGallery(
    playlists: List<Playlist>,
    selectedPlaylistId: Int?,
    currentPlaylistItems: List<PlaylistItem>,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Int) -> Unit,
    onSelectPlaylist: (Int?) -> Unit,
    onRemovePlaylistItem: (Int) -> Unit,
    onPlayPlaylistItem: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (selectedPlaylistId == null) {
            // List of Playlists
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Playlists",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("create_playlist_header_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Playlist", fontSize = 12.sp)
                    }
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "No playlists found.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Button(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("create_first_playlist_button")
                            ) {
                                Text("Create First Playlist")
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(playlists) { playlist ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlaylist(playlist.id) }
                                    .testTag("playlist_card_${playlist.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF8E44AD), Color(0xFF3498DB))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = playlist.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Playlist",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )

                                        IconButton(
                                            onClick = { onDeletePlaylist(playlist.id) },
                                            modifier = Modifier.size(24.dp).testTag("delete_playlist_${playlist.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Playlist",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Selected Playlist Details View
            val playlistName = playlists.find { it.id == selectedPlaylistId }?.name ?: "Playlist"

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onSelectPlaylist(null) },
                            modifier = Modifier.testTag("playlist_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to playlists",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = playlistName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (currentPlaylistItems.isNotEmpty()) {
                        Button(
                            onClick = { onPlayPlaylistItem(currentPlaylistItems.first()) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("play_all_playlist_button")
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All", fontSize = 12.sp)
                        }
                    }
                }

                if (currentPlaylistItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "This playlist is empty.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Go to Internet Feeds, Local Gallery or Server to add files.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(currentPlaylistItems) { item ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayPlaylistItem(item) }
                                    .testTag("playlist_item_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val (icon, color) = if (item.isVideo) {
                                            Pair(Icons.Default.Videocam, Color(0xFF2ECC71))
                                        } else {
                                            Pair(Icons.Default.MusicNote, Color(0xFFE74C3C))
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = item.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.format,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onRemovePlaylistItem(item.id) },
                                        modifier = Modifier.testTag("remove_playlist_item_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove From Playlist",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create Playlist Dialog
        if (showCreateDialog) {
            Dialog(onDismissRequest = { showCreateDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("create_playlist_dialog")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Create New Playlist",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            placeholder = { Text("e.g. Chill Music", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("create_playlist_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                playlistNameInput = ""
                                showCreateDialog = false
                            }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (playlistNameInput.isNotBlank()) {
                                        onCreatePlaylist(playlistNameInput.trim())
                                        playlistNameInput = ""
                                        showCreateDialog = false
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("confirm_create_playlist_button")
                            ) {
                                Text("Create")
                            }
                        }
                    }
                }
            }
        }
    }
}
