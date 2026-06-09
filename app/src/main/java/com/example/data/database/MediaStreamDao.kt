package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaStreamDao {
    @Query("SELECT * FROM media_streams ORDER BY timestamp DESC")
    fun getAllStreams(): Flow<List<MediaStream>>

    @Query("SELECT * FROM media_streams WHERE id = :id LIMIT 1")
    suspend fun getStreamById(id: Int): MediaStream?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: MediaStream)

    @Query("UPDATE media_streams SET lastPositionMs = :positionMs, playbackSpeed = :speed WHERE id = :id")
    suspend fun updatePlaybackState(id: Int, positionMs: Long, speed: Float)

    @Query("DELETE FROM media_streams WHERE id = :id")
    suspend fun deleteStreamById(id: Int)

    @Query("DELETE FROM media_streams WHERE isCustom = 1")
    suspend fun clearCustomStreams()
}
