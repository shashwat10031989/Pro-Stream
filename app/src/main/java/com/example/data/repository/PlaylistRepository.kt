package com.example.data.repository

import com.example.data.database.Playlist
import com.example.data.database.PlaylistItem
import com.example.data.database.PlaylistDao
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val playlistDao: PlaylistDao) {
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    suspend fun insertPlaylist(playlist: Playlist): Long = playlistDao.insertPlaylist(playlist)

    suspend fun deletePlaylist(id: Int) {
        playlistDao.deletePlaylist(id)
        playlistDao.clearPlaylistItems(id)
    }

    fun getItemsForPlaylist(playlistId: Int): Flow<List<PlaylistItem>> =
        playlistDao.getItemsForPlaylist(playlistId)

    suspend fun insertPlaylistItem(item: PlaylistItem) = playlistDao.insertPlaylistItem(item)

    suspend fun deletePlaylistItem(itemId: Int) = playlistDao.deletePlaylistItem(itemId)

    suspend fun clearPlaylist(playlistId: Int) = playlistDao.clearPlaylistItems(playlistId)
}
