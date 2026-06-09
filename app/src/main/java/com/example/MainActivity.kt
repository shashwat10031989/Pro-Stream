package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.MediaStream
import com.example.ui.components.StreamDashboard
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.AppTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LocalMediaFile
import com.example.ui.viewmodel.StreamViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: StreamViewModel = viewModel(
                factory = StreamViewModel.Factory(application)
            )
            val appTheme by viewModel.appTheme.collectAsState()

            MyApplicationTheme(appTheme = appTheme) {
                val allStreams by viewModel.allStreams.collectAsState()
                val playingStream by viewModel.playingStream.collectAsState()
                val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
                val scannedMediaFiles by viewModel.scannedMediaFiles.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()
                val networkServers by viewModel.networkServers.collectAsState()
                val isDiscovering by viewModel.isDiscovering.collectAsState()
                val currentServer by viewModel.currentServer.collectAsState()
                val currentServerFiles by viewModel.currentServerFiles.collectAsState()
                val currentPath by viewModel.currentPath.collectAsState()
                val isBrowsingServer by viewModel.isBrowsingServer.collectAsState()
                val playlists by viewModel.playlists.collectAsState()
                val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
                val currentPlaylistItems by viewModel.currentPlaylistItems.collectAsState()

                // Keep track of orientation adjustments on player opening
                DisposableEffect(playingStream) {
                    if (playingStream != null) {
                        // Automatically lock screen landscape for a movie-theater feeling in the video player
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        // Unlock / reset to sensors configuration when returning to streams list
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    onDispose {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (playingStream != null) {
                        VideoPlayer(
                            stream = playingStream!!,
                            onClosePlayer = { lastPos, speed ->
                                viewModel.updatePlaybackState(playingStream!!.id, lastPos, speed)
                                viewModel.selectStream(null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            StreamDashboard(
                                streams = allStreams,
                                recentlyPlayed = recentlyPlayed,
                                onStreamSelected = { stream ->
                                    viewModel.selectStream(stream)
                                },
                                onAddStream = { title, url, subUrl, format ->
                                    viewModel.addCustomStream(title, url, subUrl, format)
                                },
                                onDeleteStream = { id ->
                                    viewModel.deleteStream(id)
                                },
                                currentTheme = appTheme,
                                onThemeToggle = { theme ->
                                    viewModel.setAppTheme(theme)
                                },
                                scannedFiles = scannedMediaFiles,
                                isScanning = isScanning,
                                onScanClick = {
                                    viewModel.scanStorage()
                                },
                                onSeedDemoClick = {
                                    viewModel.seedDemoLocalFiles()
                                },
                                onLocalFavoriteToggle = { id ->
                                    viewModel.toggleLocalFileFavorite(id)
                                },
                                networkServers = networkServers,
                                isDiscovering = isDiscovering,
                                currentServer = currentServer,
                                currentServerFiles = currentServerFiles,
                                currentPath = currentPath,
                                isBrowsingServer = isBrowsingServer,
                                onDiscoverServers = { viewModel.discoverNetworkServers() },
                                onAddNetworkServer = { name, proto, host, port, path ->
                                    viewModel.addNetworkServer(name, proto, host, port, path)
                                },
                                onRemoveNetworkServer = { id -> viewModel.removeNetworkServer(id) },
                                onBrowseServer = { server -> viewModel.browseServer(server) },
                                onNavigateToPath = { path -> viewModel.navigateToPath(path) },
                                onGoBackDirectory = { viewModel.goBackDirectory() },
                                onDisconnectServer = { viewModel.disconnectServer() },
                                playlists = playlists,
                                selectedPlaylistId = selectedPlaylistId,
                                currentPlaylistItems = currentPlaylistItems,
                                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                                onSelectPlaylist = { id -> viewModel.selectPlaylist(id) },
                                onAddItemToPlaylist = { playlistId, title, url, isVideo, format, duration ->
                                    viewModel.addItemToPlaylist(playlistId, title, url, isVideo, format, duration)
                                },
                                onRemovePlaylistItem = { itemId -> viewModel.removePlaylistItem(itemId) },
                                onPlayPlaylistItem = { item ->
                                    val mapped = MediaStream(
                                        id = (item.id + 50000) * -1,
                                        title = item.title,
                                        url = item.urlOrUri,
                                        format = item.format,
                                        subtitleUrl = item.subtitleUrl,
                                        isCustom = true
                                    )
                                    viewModel.selectStream(mapped)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
