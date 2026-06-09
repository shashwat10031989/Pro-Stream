package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.MediaStream
import com.example.data.database.Playlist
import com.example.data.database.PlaylistItem
import com.example.data.repository.StreamRepository
import com.example.data.repository.PlaylistRepository
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class LocalMediaFile(
    val id: Long,
    val title: String,
    val uriString: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val mimeType: String?,
    val isFavorite: Boolean = false
)

data class NetworkServer(
    val id: String,
    val name: String,
    val protocol: String, // "DLNA", "SMB", "WebDAV", "FTP"
    val host: String,
    val port: Int?,
    val path: String?,
    val isDiscovered: Boolean = false
)

data class NetworkFile(
    val name: String,
    val isDirectory: Boolean,
    val path: String,
    val url: String,
    val sizeBytes: Long? = null,
    val isVideo: Boolean = true
)

class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = StreamRepository(database.mediaStreamDao())
    private val playlistRepository = PlaylistRepository(database.playlistDao())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _selectedPlaylistId = MutableStateFlow<Int?>(null)
    val selectedPlaylistId: StateFlow<Int?> = _selectedPlaylistId.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.allPlaylists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentPlaylistItems: StateFlow<List<PlaylistItem>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else playlistRepository.getItemsForPlaylist(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectPlaylist(playlistId: Int?) {
        _selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.insertPlaylist(Playlist(name = name))
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun addItemToPlaylist(playlistId: Int, fileTitle: String, fileUrlOrUri: String, isVideo: Boolean, format: String = "AUTO", durationMs: Long = 0L) {
        viewModelScope.launch {
            val item = PlaylistItem(
                playlistId = playlistId,
                title = fileTitle,
                urlOrUri = fileUrlOrUri,
                isVideo = isVideo,
                format = format,
                durationMs = durationMs
            )
            playlistRepository.insertPlaylistItem(item)
        }
    }

    fun removePlaylistItem(itemId: Int) {
        viewModelScope.launch {
            playlistRepository.deletePlaylistItem(itemId)
        }
    }

    private val sharedPrefs = application.getSharedPreferences("pro_stream_settings", android.content.Context.MODE_PRIVATE)

    private val _networkServers = MutableStateFlow<List<NetworkServer>>(emptyList())
    val networkServers: StateFlow<List<NetworkServer>> = _networkServers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _currentServer = MutableStateFlow<NetworkServer?>(null)
    val currentServer: StateFlow<NetworkServer?> = _currentServer.asStateFlow()

    private val _currentServerFiles = MutableStateFlow<List<NetworkFile>>(emptyList())
    val currentServerFiles: StateFlow<List<NetworkFile>> = _currentServerFiles.asStateFlow()

    private val _currentPath = MutableStateFlow<String>("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _isBrowsingServer = MutableStateFlow(false)
    val isBrowsingServer: StateFlow<Boolean> = _isBrowsingServer.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<MediaStream>>(emptyList())
    val recentlyPlayed: StateFlow<List<MediaStream>> = _recentlyPlayed.asStateFlow()

    init {
        _networkServers.value = loadServers()
        _recentlyPlayed.value = loadRecentlyPlayed()
    }

    private fun saveServers(servers: List<NetworkServer>) {
        val serialized = servers.joinToString(";") { server ->
            listOf(
                server.id,
                server.name,
                server.protocol,
                server.host,
                server.port?.toString() ?: "null",
                server.path ?: "null",
                server.isDiscovered.toString()
            ).joinToString("|")
        }
        sharedPrefs.edit().putString("nas_servers_list", serialized).apply()
    }

    private fun loadServers(): List<NetworkServer> {
        val serialized = sharedPrefs.getString("nas_servers_list", null) ?: return defaultDiscoveredServers()
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split(";").map { item ->
                val parts = item.split("|")
                NetworkServer(
                    id = parts[0],
                    name = parts[1],
                    protocol = parts[2],
                    host = parts[3],
                    port = if (parts[4] == "null") null else parts[4].toIntOrNull(),
                    path = if (parts[5] == "null") null else parts[5],
                    isDiscovered = parts[6].toBoolean()
                )
            }
        } catch (e: Exception) {
            defaultDiscoveredServers()
        }
    }

    private fun saveRecentlyPlayed(list: List<MediaStream>) {
        val serialized = list.joinToString("\u001E") { stream ->
            listOf(
                stream.id.toString(),
                stream.title,
                stream.url,
                stream.subtitleUrl ?: "",
                stream.format,
                stream.isCustom.toString(),
                stream.lastPositionMs.toString(),
                stream.playbackSpeed.toString()
            ).joinToString("\u001F")
        }
        sharedPrefs.edit().putString("recently_played_media", serialized).apply()
    }

    private fun loadRecentlyPlayed(): List<MediaStream> {
        val serialized = sharedPrefs.getString("recently_played_media", null) ?: return emptyList()
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("\u001E").map { item ->
                val parts = item.split("\u001F")
                MediaStream(
                    id = parts[0].toInt(),
                    title = parts[1],
                    url = parts[2],
                    subtitleUrl = parts[3].ifEmpty { null },
                    format = parts[4],
                    isCustom = parts[5].toBoolean(),
                    lastPositionMs = parts[6].toLongOrNull() ?: 0L,
                    playbackSpeed = parts[7].toFloatOrNull() ?: 1.0f
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun defaultDiscoveredServers(): List<NetworkServer> {
        return listOf(
            NetworkServer("s_1", "Synology DS920+ Media [DLNA]", "DLNA", "192.168.1.105", 50001, "/upnp/control/media", true),
            NetworkServer("s_2", "TrueNAS Core Share [SMB]", "SMB", "192.168.1.112", 445, "volume1/media", true),
            NetworkServer("s_3", "Plex DLNA Server", "DLNA", "192.168.1.5", 32400, "/active_media", true)
        )
    }

    fun discoverNetworkServers() {
        viewModelScope.launch {
            _isDiscovering.value = true
            delay(1500) // Realistic scanning animations and delay
            val current = loadServers().toMutableList()
            val defaults = defaultDiscoveredServers()
            defaults.forEach { d ->
                if (current.none { it.id == d.id }) {
                    current.add(d)
                }
            }
            saveServers(current)
            _networkServers.value = current
            _isDiscovering.value = false
        }
    }

    fun addNetworkServer(name: String, protocol: String, host: String, port: Int?, path: String?) {
        val newId = "s_user_${System.currentTimeMillis()}"
        val newServer = NetworkServer(
            id = newId,
            name = name.ifBlank { "$protocol Server ($host)" },
            protocol = protocol,
            host = host.ifBlank { "127.0.0.1" },
            port = port,
            path = path?.ifBlank { null }
        )
        val updated = _networkServers.value.filter { it.id != newId } + newServer
        saveServers(updated)
        _networkServers.value = updated
    }

    fun removeNetworkServer(serverId: String) {
        val updated = _networkServers.value.filter { it.id != serverId }
        saveServers(updated)
        _networkServers.value = updated
        if (_currentServer.value?.id == serverId) {
            disconnectServer()
        }
    }

    fun browseServer(server: NetworkServer) {
        _currentServer.value = server
        _currentPath.value = "/"
        _isBrowsingServer.value = true
        loadFilesForPath(server, "/")
    }

    fun navigateToPath(path: String) {
        val server = _currentServer.value ?: return
        _currentPath.value = path
        loadFilesForPath(server, path)
    }

    fun goBackDirectory() {
        val path = _currentPath.value
        if (path == "/") {
            disconnectServer()
            return
        }
        val segments = path.split("/").filter { it.isNotEmpty() }
        if (segments.size <= 1) {
            navigateToPath("/")
        } else {
            val parentPath = "/" + segments.dropLast(1).joinToString("/")
            navigateToPath(parentPath)
        }
    }

    fun disconnectServer() {
        _isBrowsingServer.value = false
        _currentServer.value = null
        _currentServerFiles.value = emptyList()
        _currentPath.value = "/"
    }

    fun loadFilesForPath(server: NetworkServer, path: String) {
        viewModelScope.launch {
            _currentServerFiles.value = emptyList() // Trigger loading loader in UI
            delay(400) // Beautiful network latency simulation
            
            val files = mutableListOf<NetworkFile>()
            when (path) {
                "/" -> {
                    files.add(NetworkFile("Movies & Cinema", isDirectory = true, path = "/Movies", url = ""))
                    files.add(NetworkFile("TV Shows", isDirectory = true, path = "/TV Shows", url = ""))
                    files.add(NetworkFile("Lossless Audio Library", isDirectory = true, path = "/Music", url = ""))
                    
                    if (server.protocol == "WebDAV" || server.protocol == "FTP") {
                        files.add(NetworkFile("Backups & Archives", isDirectory = true, path = "/Archives", url = ""))
                    } else {
                        files.add(NetworkFile("Shared Public Media", isDirectory = true, path = "/Shared", url = ""))
                    }
                }
                "/Movies" -> {
                    files.add(NetworkFile("Sintel Sci-Fi Movie (Full HD)", isDirectory = false, path = "/Movies/Sintel.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", sizeBytes = 243912000L, isVideo = true))
                    files.add(NetworkFile("Tears of Steel (VFX Demo 4K)", isDirectory = false, path = "/Movies/TearsOfSteel.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", sizeBytes = 554129000L, isVideo = true))
                    files.add(NetworkFile("Big Buck Bunny Classic", isDirectory = false, path = "/Movies/BigBuckBunny.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", sizeBytes = 158220100L, isVideo = true))
                    files.add(NetworkFile("Elephant's Dream (Open Source)", isDirectory = false, path = "/Movies/ElephantsDream.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", sizeBytes = 328000000L, isVideo = true))
                }
                "/TV Shows" -> {
                    files.add(NetworkFile("Sublette Live Show S01", isDirectory = true, path = "/TV Shows/Sublette", url = ""))
                    files.add(NetworkFile("Tears of Steel Behind The Scenes", isDirectory = false, path = "/TV Shows/BTS.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubtitlesVideo.mp4", sizeBytes = 89000000L, isVideo = true))
                }
                "/TV Shows/Sublette" -> {
                    files.add(NetworkFile("E01: First Steps", isDirectory = false, path = "/TV Shows/Sublette/E01.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", sizeBytes = 44200000L, isVideo = true))
                    files.add(NetworkFile("E02: Discovery", isDirectory = false, path = "/TV Shows/Sublette/E02.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", sizeBytes = 49100000L, isVideo = true))
                    files.add(NetworkFile("E03: Final Stand", isDirectory = false, path = "/TV Shows/Sublette/E03.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", sizeBytes = 53400000L, isVideo = true))
                }
                "/Music" -> {
                    files.add(NetworkFile("SoundHelix Classic Orchestral 1", isDirectory = false, path = "/Music/Helix1.mp3", url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", sizeBytes = 12000000L, isVideo = false))
                    files.add(NetworkFile("Guitar Fusion Dream", isDirectory = false, path = "/Music/Helix3.mp3", url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", sizeBytes = 10500000L, isVideo = false))
                    files.add(NetworkFile("Chillwave Lounge Synth 5", isDirectory = false, path = "/Music/Helix5.mp3", url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", sizeBytes = 9800000L, isVideo = false))
                    files.add(NetworkFile("Techno Uplifting Odyssey", isDirectory = false, path = "/Music/Helix8.mp3", url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3", sizeBytes = 14200000L, isVideo = false))
                }
                "/Archives" -> {
                    // Empty list state demo
                }
                "/Shared" -> {
                    files.add(NetworkFile("Public Drone Capture", isDirectory = false, path = "/Shared/Drone.mp4", url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", sizeBytes = 122000000L, isVideo = true))
                }
            }
            _currentServerFiles.value = files
        }
    }
    
    private val _scannedMediaFiles = MutableStateFlow<List<LocalMediaFile>>(emptyList())
    val scannedMediaFiles: StateFlow<List<LocalMediaFile>> = _scannedMediaFiles.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private fun getSavedFavorites(): Set<String> {
        return sharedPrefs.getStringSet("favorite_media_uris", emptySet()) ?: emptySet()
    }

    fun toggleLocalFileFavorite(fileId: Long) {
        val currentScannedList = _scannedMediaFiles.value
        val file = currentScannedList.find { it.id == fileId } ?: return
        val currentFavorites = getSavedFavorites().toMutableSet()
        val newFavoriteState = !file.isFavorite
        if (newFavoriteState) {
            currentFavorites.add(file.uriString)
        } else {
            currentFavorites.remove(file.uriString)
        }
        sharedPrefs.edit().putStringSet("favorite_media_uris", currentFavorites).apply()

        _scannedMediaFiles.value = currentScannedList.map {
            if (it.id == fileId) {
                it.copy(isFavorite = newFavoriteState)
            } else {
                it
            }
        }
    }

    fun scanStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val list = mutableListOf<LocalMediaFile>()
            val contentResolver = getApplication<Application>().contentResolver
            val favs = getSavedFavorites()

            // 1. Scan Video
            val videoProjection = arrayOf(
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                android.provider.MediaStore.Video.Media.DURATION,
                android.provider.MediaStore.Video.Media.SIZE,
                android.provider.MediaStore.Video.Media.MIME_TYPE
            )
            val videoUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            try {
                contentResolver.query(videoUri, videoProjection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                    val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
                    val mimeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown Video"
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val mime = cursor.getString(mimeColumn)
                        val contentUri = android.content.ContentUris.withAppendedId(videoUri, id).toString()

                        list.add(
                            LocalMediaFile(
                                id = id,
                                title = name,
                                uriString = contentUri,
                                durationMs = duration,
                                sizeBytes = size,
                                isVideo = true,
                                mimeType = mime,
                                isFavorite = favs.contains(contentUri)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Scan Audio
            val audioProjection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.SIZE,
                android.provider.MediaStore.Audio.Media.MIME_TYPE
            )
            val audioUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            try {
                contentResolver.query(audioUri, audioProjection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                    val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.SIZE)
                    val mimeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown Audio"
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val mime = cursor.getString(mimeColumn)
                        val contentUri = android.content.ContentUris.withAppendedId(audioUri, id).toString()

                        list.add(
                            LocalMediaFile(
                                id = id,
                                title = name,
                                uriString = contentUri,
                                durationMs = duration,
                                sizeBytes = size,
                                isVideo = false,
                                mimeType = mime,
                                isFavorite = favs.contains(contentUri)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _scannedMediaFiles.value = list.sortedByDescending { it.id }
            _isScanning.value = false
        }
    }

    fun seedDemoLocalFiles() {
        val favs = getSavedFavorites()
        _scannedMediaFiles.value = listOf(
            LocalMediaFile(
                id = 1001L,
                title = "Big Buck Bunny (Local Video Simulator)",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                durationMs = 596000L,
                sizeBytes = 10485760L * 15,
                isVideo = true,
                mimeType = "video/mp4",
                isFavorite = favs.contains("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            ),
            LocalMediaFile(
                id = 1002L,
                title = "Sintel Movie Demo (Local Video Simulator)",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                durationMs = 888000L,
                sizeBytes = 10485760L * 24,
                isVideo = true,
                mimeType = "video/mp4",
                isFavorite = favs.contains("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4")
            ),
            LocalMediaFile(
                id = 1003L,
                title = "Cosmic Synthesizer Synthwave (Ambient Audio)",
                uriString = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                durationMs = 372000L,
                sizeBytes = 1048576L * 8,
                isVideo = false,
                mimeType = "audio/mp3",
                isFavorite = favs.contains("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
            ),
            LocalMediaFile(
                id = 1004L,
                title = "Cyber Ambient Chill Beat (Ambient Audio)",
                uriString = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                durationMs = 302000L,
                sizeBytes = 1048576L * 6,
                isVideo = false,
                mimeType = "audio/mp3",
                isFavorite = favs.contains("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3")
            )
        )
    }
    
    private val _appTheme = MutableStateFlow(getSavedTheme())
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private fun getSavedTheme(): AppTheme {
        val saved = sharedPrefs.getString("app_theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return try {
            AppTheme.valueOf(saved)
        } catch (_: Exception) {
            AppTheme.SYSTEM
        }
    }

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        sharedPrefs.edit().putString("app_theme", theme.name).apply()
    }

    val allStreams: StateFlow<List<MediaStream>> = repository.allStreams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _playingStream = MutableStateFlow<MediaStream?>(null)
    val playingStream: StateFlow<MediaStream?> = _playingStream.asStateFlow()

    fun selectStream(stream: MediaStream?) {
        _playingStream.value = stream
        if (stream != null) {
            val currentList = _recentlyPlayed.value.toMutableList()
            currentList.removeAll { it.url == stream.url || (it.id != 0 && it.id == stream.id) }
            currentList.add(0, stream)
            val updatedList = currentList.take(5)
            _recentlyPlayed.value = updatedList
            saveRecentlyPlayed(updatedList)
        }
    }

    fun addCustomStream(title: String, url: String, subtitleUrl: String?, format: String) {
        viewModelScope.launch {
            val formattedFormat = if (format == "Auto") "AUTO" else format.uppercase()
            val stream = MediaStream(
                title = title.ifEmpty { "Custom Stream" },
                url = url,
                subtitleUrl = subtitleUrl?.trim()?.ifEmpty { null },
                format = formattedFormat,
                isCustom = true
            )
            repository.insertStream(stream)
        }
    }

    fun deleteStream(id: Int) {
        viewModelScope.launch {
            repository.deleteStreamById(id)
            if (_playingStream.value?.id == id) {
                _playingStream.value = null
            }
        }
    }

    fun updatePlaybackState(id: Int, positionMs: Long, speed: Float) {
        viewModelScope.launch {
            repository.updatePlaybackState(id, positionMs, speed)
            _playingStream.value?.let { current ->
                if (current.id == id) {
                    _playingStream.value = current.copy(
                        lastPositionMs = positionMs,
                        playbackSpeed = speed
                    )
                }
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StreamViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StreamViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
