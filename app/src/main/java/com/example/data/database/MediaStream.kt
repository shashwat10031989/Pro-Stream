package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_streams")
data class MediaStream(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val subtitleUrl: String? = null,
    val format: String = "AUTO", // "AUTO", "HLS", "DASH", "MP4"
    val playbackSpeed: Float = 1.0f,
    val lastPositionMs: Long = 0L,
    val isCustom: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
