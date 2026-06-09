package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Brush
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import com.example.data.database.MediaStream
import com.example.ui.model.TrackInfo
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    stream: MediaStream,
    onClosePlayer: (lastPositionMs: Long, speed: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = remember { context as? Activity }

    // ExoPlayer state
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(stream.lastPositionMs) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(stream.playbackSpeed) }
    var isMuted by remember { mutableStateOf(false) }

    // Sleep Timer states
    var sleepTimerSecondsRemaining by remember { mutableIntStateOf(0) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Cast states
    var isCasting by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<String?>(null) }
    var showCastDialog by remember { mutableStateOf(false) }
    var isCastingPlaying by remember { mutableStateOf(true) }

    // Track selections
    var currentTracksInfo by remember { mutableStateOf<Tracks?>(null) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    // UI overlays controls
    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Gesture control values
    var brightness by remember { mutableFloatStateOf(getAppBrightness(activity)) }
    var volumeLevel by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Highlight gesture overlays (briefly shown)
    var activeGestureType by remember { mutableStateOf<String?>(null) } // "VOLUME", "BRIGHTNESS", "SEEK"
    var activeGestureValue by remember { mutableStateOf(0) }
    val gestureResetHandler = remember { Handler(Looper.getMainLooper()) }
    val gestureResetRunnable = remember { Runnable { activeGestureType = null } }

    fun triggerGestureOverlay(type: String, value: Int) {
        activeGestureType = type
        activeGestureValue = value
        gestureResetHandler.removeCallbacks(gestureResetRunnable)
        gestureResetHandler.postDelayed(gestureResetRunnable, 1500)
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            kotlinx.coroutines.delay(4000)
            controlsVisible = false
        }
    }

    // Custom back handler
    BackHandler {
        player?.let { p ->
            onClosePlayer(p.currentPosition, p.playbackParameters.speed)
        } ?: onClosePlayer(stream.lastPositionMs, playbackSpeed)
    }

    // Initialize ExoPlayer
    DisposableEffect(stream) {
        val exoplayer = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setPlaybackSpeed(playbackSpeed)

            val subtitleMimeType = if (stream.subtitleUrl?.endsWith(".srt", ignoreCase = true) == true) {
                MimeTypes.APPLICATION_SUBRIP
            } else {
                MimeTypes.TEXT_VTT
            }

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(stream.url))

            if (!stream.subtitleUrl.isNullOrEmpty()) {
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(stream.subtitleUrl))
                    .setMimeType(subtitleMimeType)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }

            setMediaItem(mediaItemBuilder.build())
            seekTo(currentPosition)
            prepare()
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }

            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exoplayer.duration
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracksInfo = tracks
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
            }
        }

        exoplayer.addListener(listener)
        player = exoplayer

        onDispose {
            exoplayer.removeListener(listener)
            exoplayer.release()
            player = null
        }
    }

    // Position updates tracker
    LaunchedEffect(isPlaying, isCasting, isCastingPlaying, duration) {
        if (isCasting) {
            while (isCastingPlaying) {
                if (duration > 0 && currentPosition < duration) {
                    currentPosition = (currentPosition + 1000L).coerceAtMost(duration)
                } else if (duration > 0) {
                    isCastingPlaying = false
                }
                kotlinx.coroutines.delay(1000L)
            }
        } else {
            while (isPlaying) {
                player?.let { p ->
                    currentPosition = p.currentPosition
                }
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    // Sleep Timer countdown tracker
    val isAnyPlaying = if (isCasting) isCastingPlaying else isPlaying
    LaunchedEffect(sleepTimerSecondsRemaining, isAnyPlaying, isCasting) {
        if (sleepTimerSecondsRemaining > 0 && isAnyPlaying) {
            kotlinx.coroutines.delay(1000L)
            sleepTimerSecondsRemaining--
            if (sleepTimerSecondsRemaining == 0) {
                if (isCasting) {
                    isCastingPlaying = false
                } else {
                    player?.pause()
                }
            }
        }
    }

    fun toggleMute() {
        player?.let { p ->
            isMuted = !isMuted
            p.volume = if (isMuted) 0f else 1.0f
        }
    }

    fun skipBackward() {
        if (isCasting) {
            val seekPos = (currentPosition - 10000L).coerceAtLeast(0L)
            currentPosition = seekPos
            triggerGestureOverlay("SEEK", -10)
        } else {
            player?.let { p ->
                val seekPos = (p.currentPosition - 10000L).coerceAtLeast(0L)
                p.seekTo(seekPos)
                currentPosition = seekPos
                triggerGestureOverlay("SEEK", -10)
            }
        }
    }

    fun skipForward() {
        if (isCasting) {
            val seekPos = (currentPosition + 10000L).coerceAtMost(duration)
            currentPosition = seekPos
            triggerGestureOverlay("SEEK", 10)
        } else {
            player?.let { p ->
                val seekPos = (p.currentPosition + 10000L).coerceAtMost(duration)
                p.seekTo(seekPos)
                currentPosition = seekPos
                triggerGestureOverlay("SEEK", 10)
            }
        }
    }

    // Tracks information filter
    val subtitleTracks = remember(currentTracksInfo) {
        currentTracksInfo?.let { getTracksOfType(it, C.TRACK_TYPE_TEXT) } ?: emptyList()
    }

    val audioTracks = remember(currentTracksInfo) {
        currentTracksInfo?.let { getTracksOfType(it, C.TRACK_TYPE_AUDIO) } ?: emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_container")
    ) {
        // Video render canvas
        if (isCasting) {
            CastingRepresentationScreen(
                connectedDevice = connectedDevice ?: "Smart TV",
                streamTitle = stream.title,
                streamFormat = stream.format,
                isCastingPlaying = isCastingPlaying,
                currentPosition = currentPosition,
                duration = duration,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isScreenLocked) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                            },
                            onDoubleTap = { offset ->
                                if (!isScreenLocked) {
                                    if (offset.x < size.width / 2) {
                                        skipBackward()
                                    } else {
                                        skipForward()
                                    }
                                }
                            }
                        )
                    }
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.resizeMode = resizeMode
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isScreenLocked) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                            },
                            onDoubleTap = { offset ->
                                if (!isScreenLocked) {
                                    if (offset.x < size.width / 2) {
                                        skipBackward()
                                    } else {
                                        skipForward()
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(isScreenLocked) {
                        if (isScreenLocked) return@pointerInput
                        var isLeftSide = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                isLeftSide = offset.x < size.width / 2
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (isLeftSide) {
                                    val delta = -dragAmount.y / size.height
                                    brightness = (brightness + delta).coerceIn(0.01f, 1.0f)
                                    setAppBrightness(activity, brightness)
                                    triggerGestureOverlay("BRIGHTNESS", (brightness * 100).roundToInt())
                                } else {
                                    val delta = -dragAmount.y / size.height
                                    val currentValFloat = volumeLevel.toFloat() / maxVolume
                                    val nextValFloat = (currentValFloat + delta).coerceIn(0f, 1f)
                                    val nextVolVal = (nextValFloat * maxVolume).roundToInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolVal, 0)
                                    volumeLevel = nextVolVal
                                    triggerGestureOverlay("VOLUME", (nextValFloat * 100).roundToInt())
                                }
                            }
                        )
                    }
            )
        }

        // Subtitle layer background container to force high-legibility subtitle styling
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
                    .testTag("video_player_loading")
            )
        }

        // Gesture indicators visual flash overlay
        AnimatedVisibility(
            visible = activeGestureType != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            activeGestureType?.let { type ->
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val icon = when (type) {
                            "VOLUME" -> {
                                if (activeGestureValue == 0) Icons.Default.VolumeMute
                                else if (activeGestureValue < 50) Icons.Default.VolumeDown
                                else Icons.Default.VolumeUp
                            }
                            "BRIGHTNESS" -> Icons.Outlined.BrightnessMedium
                            else -> if (activeGestureValue < 0) Icons.Default.FastRewind else Icons.Default.FastForward
                        }

                        val txt = when (type) {
                            "SEEK" -> "${if (activeGestureValue > 0) "+" else ""}${activeGestureValue}s"
                            else -> "$activeGestureValue%"
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = txt,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Playback Controllers Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -40 }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            player?.let { p ->
                                onClosePlayer(p.currentPosition, p.playbackParameters.speed)
                            } ?: onClosePlayer(stream.lastPositionMs, playbackSpeed)
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Library",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stream.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        val protocolLabel = stream.format
                        Text(
                            text = "Live Stream Type: $protocolLabel",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isScreenLocked) {
                            if (sleepTimerSecondsRemaining > 0) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                        .clickable { showSleepTimerDialog = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("sleep_timer_pill"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Snooze,
                                        contentDescription = "Sleep Timer Active",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = formatRemainingTime(sleepTimerSecondsRemaining),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { showSleepTimerDialog = true },
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .testTag("sleep_timer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Snooze,
                                        contentDescription = "Set Sleep Timer",
                                        tint = Color.White
                                    )
                                }
                            }
                        } else if (sleepTimerSecondsRemaining > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("sleep_timer_pill_locked"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Snooze,
                                    contentDescription = "Sleep Timer Active",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = formatRemainingTime(sleepTimerSecondsRemaining),
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!isScreenLocked) {
                            IconButton(
                                onClick = { showCastDialog = true },
                                modifier = Modifier
                                    .background(
                                        if (isCasting) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                    .testTag("player_cast_button")
                            ) {
                                Icon(
                                    imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                    contentDescription = if (isCasting) "Casting Active" else "Cast Stream",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { isScreenLocked = !isScreenLocked },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("player_lock_button")
                        ) {
                            Icon(
                                imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock Controls",
                                tint = if (isScreenLocked) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                }

                // If not locked, display play, pause, rewind, fast forward center buttons
                if (!isScreenLocked) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { skipBackward() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "-10 Sec",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        IconButton(
                            onClick = {
                                if (isCasting) {
                                    isCastingPlaying = !isCastingPlaying
                                } else {
                                    player?.let { p ->
                                        if (p.isPlaying) {
                                            p.pause()
                                        } else {
                                            p.play()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("player_play_pause_button")
                        ) {
                            val activePlayState = if (isCasting) isCastingPlaying else isPlaying
                            Icon(
                                imageVector = if (activePlayState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (activePlayState) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        IconButton(
                            onClick = { skipForward() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "+10 Sec",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Bottom control panel
                if (!isScreenLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .align(Alignment.BottomCenter)
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        // Progress seek slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.width(55.dp)
                            )

                            Slider(
                                value = currentPosition.toFloat(),
                                onValueChange = { newValue ->
                                    currentPosition = newValue.toLong()
                                    if (!isCasting) {
                                        player?.seekTo(newValue.toLong())
                                    }
                                },
                                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.DarkGray,
                                    thumbColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("player_scrub_slider")
                            )

                            Text(
                                text = formatTime(duration),
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.width(55.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Controls icons layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left-group: Mute, Volume controls (buttons and slider), Aspect ratio toggler
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                IconButton(
                                    onClick = { toggleMute() },
                                    modifier = Modifier.testTag("player_mute_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute Toggle",
                                        tint = Color.White
                                    )
                                }

                                // Volume down button
                                IconButton(
                                    onClick = {
                                        val nextVol = (volumeLevel - 1).coerceAtLeast(0)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                        volumeLevel = nextVol
                                        triggerGestureOverlay("VOLUME", ((nextVol.toFloat() / maxVolume) * 100).roundToInt())
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("player_volume_down_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeDown,
                                        contentDescription = "Volume Down",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Volume level slider
                                Slider(
                                    value = volumeLevel.toFloat(),
                                    onValueChange = { newValue ->
                                        val vol = newValue.roundToInt().coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                                        volumeLevel = vol
                                        triggerGestureOverlay("VOLUME", ((vol.toFloat() / maxVolume) * 100).roundToInt())
                                    },
                                    valueRange = 0f..maxVolume.toFloat(),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.DarkGray,
                                        thumbColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .width(72.dp)
                                        .testTag("player_volume_slider")
                                )

                                // Volume up button
                                IconButton(
                                    onClick = {
                                        val nextVol = (volumeLevel + 1).coerceAtMost(maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                        volumeLevel = nextVol
                                        triggerGestureOverlay("VOLUME", ((nextVol.toFloat() / maxVolume) * 100).roundToInt())
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("player_volume_up_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Volume Up",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Aspect ratio / Resize selector
                                IconButton(onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    val resizeLabel = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit View"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch Fill"
                                        else -> "Zoom View"
                                    }
                                    triggerGestureOverlay("ASPECT", 100) // Dummy value just to trigger text
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "Aspect Ratio",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Right-group: Audio tracks selection, Subtitle tracks selection, Playback speed control
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Audio tracks dialogue trigger
                                IconButton(
                                    onClick = { showAudioDialog = true },
                                    enabled = audioTracks.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = "Audio Track selection",
                                        tint = if (audioTracks.isNotEmpty()) Color.White else Color.DarkGray
                                    )
                                }

                                // Subtitle tracks dialogue trigger
                                IconButton(
                                    onClick = { showSubtitleDialog = true },
                                    enabled = subtitleTracks.isNotEmpty() || !stream.subtitleUrl.isNullOrEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ClosedCaption,
                                        contentDescription = "Subtitle Track selection",
                                        tint = if (subtitleTracks.isNotEmpty() || !stream.subtitleUrl.isNullOrEmpty()) Color.White else Color.DarkGray
                                    )
                                }

                                // Playback Speed selector
                                SpeedSelectorCombo(
                                    currentSpeed = playbackSpeed,
                                    onSpeedSelected = { nextSpeed ->
                                        playbackSpeed = nextSpeed
                                        player?.setPlaybackSpeed(nextSpeed)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue choices for Subtitles
    if (showSubtitleDialog) {
        TrackSelectorDialog(
            title = "Subtitle Tracks",
            tracks = subtitleTracks,
            onTrackSelected = { track ->
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
                        )
                        .build()
                }
            },
            onDisableTrack = {
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    // Modal dialogue choices for Audio Track
    if (showAudioDialog) {
        TrackSelectorDialog(
            title = "Audio Tracks",
            tracks = audioTracks,
            onTrackSelected = { track ->
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
                        )
                        .build()
                }
            },
            onDisableTrack = null, // Audio can't be fully turned off this way, use mute
            onDismiss = { showAudioDialog = false }
        )
    }

    // Modal dialogue choices for Sleep Timer
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentSecondsRemaining = sleepTimerSecondsRemaining,
            onSetTimer = { seconds ->
                sleepTimerSecondsRemaining = seconds
            },
            onCancelTimer = {
                sleepTimerSecondsRemaining = 0
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    // Modal dialogue choices for Casting
    if (showCastDialog) {
        CastDeviceDialog(
            isCasting = isCasting,
            connectedDevice = connectedDevice,
            onConnect = { deviceName ->
                player?.pause()
                isCasting = true
                connectedDevice = deviceName
                isCastingPlaying = true
            },
            onDisconnect = {
                isCasting = false
                connectedDevice = null
                player?.seekTo(currentPosition)
                player?.play()
            },
            onDismiss = { showCastDialog = false }
        )
    }
}

// Track extractor Helper
@OptIn(UnstableApi::class)
private fun getTracksOfType(tracks: Tracks, trackType: Int): List<TrackInfo> {
    val list = mutableListOf<TrackInfo>()
    for (group in tracks.groups) {
        if (group.type == trackType) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                val languageStr = format.language?.let { " ($it)" } ?: ""
                val label = format.label ?: "Track ${list.size + 1}$languageStr"
                list.add(TrackInfo(group, i, label, isSelected))
            }
        }
    }
    return list
}

// Dialog Component for tracks selection
@Composable
fun TrackSelectorDialog(
    title: String,
    tracks: List<TrackInfo>,
    onTrackSelected: (TrackInfo) -> Unit,
    onDisableTrack: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Divider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Disable track choice option (e.g. Subtitles OFF)
                    onDisableTrack?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    it()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = false, // If none are selected, it is OFF. Hardcode for simplification of OFF toggle selection UI
                                onClick = {
                                    it()
                                    onDismiss()
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Subtitles Disabled (OFF)", fontSize = 16.sp)
                        }
                    }

                    if (tracks.isEmpty()) {
                        Text(
                            text = "No Embedded tracks found. (If you custom configured external subtitles, they are rendered automatically.)",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    tracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTrackSelected(track)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = track.isSelected,
                                onClick = {
                                    onTrackSelected(track)
                                    onDismiss()
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = track.label,
                                fontSize = 16.sp,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (track.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

// Combobox Selector for Playback Speed
@Composable
fun SpeedSelectorCombo(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Box {
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${currentSpeed}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            speeds.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${speed}x",
                            fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                            color = if (speed == currentSpeed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Screen brightness helpers
private fun getAppBrightness(activity: Activity?): Float {
    return activity?.window?.attributes?.screenBrightness?.let {
        if (it < 0f) return 0.5f else it
    } ?: 0.5f
}

private fun setAppBrightness(activity: Activity?, brightness: Float) {
    activity?.runOnUiThread {
        val window = activity.window
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }
}

// Time format helper: ms -> MM:SS or HH:MM:SS
private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// Format seconds to remaining time: MM:SS
private fun formatRemainingTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun SleepTimerDialog(
    currentSecondsRemaining: Int,
    onSetTimer: (Int) -> Unit, // passes seconds
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMinutes by remember { mutableStateOf(if (currentSecondsRemaining > 0) (currentSecondsRemaining / 60).coerceAtLeast(1) else 15) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .testTag("sleep_timer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentSecondsRemaining > 0) {
                            Text(
                                text = "Active countdown: ${formatRemainingTime(currentSecondsRemaining)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Automatically pause playback when time ends",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Presets Title
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val presets = listOf(
                    5 to "5m",
                    15 to "15m",
                    30 to "30m",
                    45 to "45m",
                    60 to "60m"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (mins, name) ->
                        val isSelected = currentSecondsRemaining > 0 && (currentSecondsRemaining / 60) == mins
                        OutlinedButton(
                            onClick = {
                                onSetTimer(mins * 60)
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.let { border ->
                                if (isSelected) null else border
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("preset_timer_$mins")
                        ) {
                            Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom Slider
                Text(
                    text = "Custom Duration",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$customMinutes minutes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Slider(
                        value = customMinutes.toFloat(),
                        onValueChange = { customMinutes = it.roundToInt().coerceIn(1, 120) },
                        valueRange = 1f..120f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_slider_bar")
                    )
                }

                // Action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentSecondsRemaining > 0) {
                        Button(
                            onClick = {
                                onCancelTimer()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_timer_button")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Turn Off", fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            onSetTimer(customMinutes * 60)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_timer_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (currentSecondsRemaining > 0) "Update" else "Start", fontSize = 13.sp)
                    }
                }

                // Close footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_timer_dialog_button")) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// Custom Model for Cast Device
data class CastDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val protocol: String
)

@Composable
fun CastingRepresentationScreen(
    connectedDevice: String,
    streamTitle: String,
    streamFormat: String,
    isCastingPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                        radius = size.minDimension / 2f
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color(0xFF0EA5E9).copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCastingPlaying) Icons.Filled.CastConnected else Icons.Filled.Cast,
                        contentDescription = "Casting media icon",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CURRENTLY CASTING",
                fontSize = 14.sp,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = streamTitle,
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = streamFormat.uppercase(),
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = "•",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = if (isCastingPlaying) "Streaming" else "Paused",
                    color = if (isCastingPlaying) Color(0xFF22C55E) else Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = connectedDevice,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Screen is in control/saver mode to conserve phone resources. Adjust progress, speeds, or stop sessions from overlay controls.",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun CastDeviceDialog(
    isCasting: Boolean,
    connectedDevice: String?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val scannedDevices = remember {
        mutableStateListOf(
            CastDevice("1", "Living Room Chromecast", "192.168.1.15", "Google Cast"),
            CastDevice("2", "Bedroom Google TV", "192.168.1.28", "Google Cast"),
            CastDevice("3", "Entertainment LG OLED", "192.168.1.44", "LG WebOS TV"),
            CastDevice("4", "Studio Samsung Link", "192.168.1.101", "Samsung Connect")
        )
    }

    var isScanning by remember { mutableStateOf(true) }
    var showAddCustomByIp by remember { mutableStateOf(false) }

    var customName by remember { mutableStateOf("") }
    var customIp by remember { mutableStateOf("") }
    var customProtocol by remember { mutableStateOf("Google Cast") }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        isScanning = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .testTag("cast_dialog_container")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Stream Cast",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isCasting) "Connected to: ${connectedDevice ?: "Smart TV"}" else "Cast stream to local networks",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                if (isCasting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Streaming Remotely",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Your device is transmitting the media stream directly to '${connectedDevice ?: "Smart TV"}' over the WLAN network.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Button(
                            onClick = {
                                onDisconnect()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("disconnect_cast_button")
                        ) {
                            Icon(imageVector = Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect Cast")
                        }
                    }
                } else {
                    if (isScanning) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Searching local network for receivers...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (showAddCustomByIp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Add Manual Smart TV Receiver",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Device Name (e.g., Living Room TV)") },
                                placeholder = { Text("TV Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_cast_name"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            )

                            OutlinedTextField(
                                value = customIp,
                                onValueChange = { customIp = it },
                                label = { Text("IP Address (e.g., 192.168.1.100)") },
                                placeholder = { Text("WLAN Network IP") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_cast_ip"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val protocols = listOf("Google Cast", "DLNA", "webOS")
                                protocols.forEach { proto ->
                                    val isSelected = customProtocol == proto
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                            )
                                            .clickable { customProtocol = proto }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = proto,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { showAddCustomByIp = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Back")
                                }

                                Button(
                                    onClick = {
                                        if (customName.isNotBlank() && customIp.isNotBlank()) {
                                            scannedDevices.add(
                                                CastDevice(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    name = customName,
                                                    ipAddress = customIp,
                                                    protocol = customProtocol
                                                )
                                            )
                                            customName = ""
                                            customIp = ""
                                            showAddCustomByIp = false
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_custom_cast_button")
                                ) {
                                    Text("Register")
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "WiFi Devices Found (${scannedDevices.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                TextButton(
                                    onClick = { showAddCustomByIp = true },
                                    modifier = Modifier.testTag("add_custom_cast_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add IP", fontSize = 12.sp)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(scannedDevices) { dev ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                            .clickable {
                                                onConnect(dev.name)
                                                onDismiss()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                            .testTag("cast_device_item_${dev.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = dev.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "WLAN IP: ${dev.ipAddress} • ${dev.protocol}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_cast_dialog_button")) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
