package com.example.data.repository

import com.example.data.database.MediaStream
import com.example.data.database.MediaStreamDao
import kotlinx.coroutines.flow.Flow

class StreamRepository(private val mediaStreamDao: MediaStreamDao) {
    val allStreams: Flow<List<MediaStream>> = mediaStreamDao.getAllStreams()

    suspend fun getStreamById(id: Int): MediaStream? = mediaStreamDao.getStreamById(id)

    suspend fun insertStream(stream: MediaStream) = mediaStreamDao.insertStream(stream)

    suspend fun updatePlaybackState(id: Int, positionMs: Long, speed: Float) =
        mediaStreamDao.updatePlaybackState(id, positionMs, speed)

    suspend fun deleteStreamById(id: Int) = mediaStreamDao.deleteStreamById(id)

    suspend fun clearCustomStreams() = mediaStreamDao.clearCustomStreams()
}
