package com.example.ui.model

import androidx.media3.common.Tracks

data class TrackInfo(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)
