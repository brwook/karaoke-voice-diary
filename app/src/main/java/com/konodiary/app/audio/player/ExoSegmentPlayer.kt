package com.konodiary.app.audio.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.konodiary.app.core.contracts.SegmentPlayer
import com.konodiary.app.core.model.Clip
import com.konodiary.app.core.model.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media3-backed player. A single ExoPlayer instance reused for every clip.
 * Must be created and driven on the main thread. See docs/SPEC.md §6.
 */
class ExoSegmentPlayer(context: Context) : SegmentPlayer {

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true,
        )
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startTicking() else stopTicking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val dur = _state.value.durationMs
                    _state.value = _state.value.copy(isPlaying = false, positionMs = dur)
                    stopTicking()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // e.g. content:// permission lost after reboot, or a deleted/corrupt
                // source. ExoPlayer goes to STATE_IDLE and would otherwise leave a
                // dead mini-player. Clean up like stop() and surface the error so the
                // UI can dismiss the mini-player (or show a message) instead of hanging.
                player.stop()
                player.clearMediaItems()
                stopTicking()
                _state.value = PlayerUiState(error = error.errorCodeName)
            }
        })
    }

    private val ticker = object : Runnable {
        override fun run() {
            pushPosition()
            handler.postDelayed(this, POSITION_INTERVAL_MS)
        }
    }

    override fun play(clip: Clip) {
        val builder = MediaItem.Builder().setUri(Uri.parse(clip.uri))
        val clipped = clip.endMs > clip.startMs
        if (clipped) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs)
                    .build()
            )
        }
        player.setMediaItem(builder.build())
        player.prepare()
        player.playWhenReady = true

        _state.value = PlayerUiState(
            clip = clip,
            isPlaying = true,
            positionMs = 0L,
            durationMs = if (clipped) clip.endMs - clip.startMs else 0L,
        )
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun resume() {
        if (_state.value.clip == null) return
        when (player.playbackState) {
            // Clip already played to the end: rewind to the clip start so pressing
            // play again re-plays it instead of staying parked at the end.
            Player.STATE_ENDED -> {
                player.seekTo(0)
                _state.value = _state.value.copy(positionMs = 0L)
            }
            // Player was reset (e.g. after an error): re-prepare before playing.
            Player.STATE_IDLE -> player.prepare()
        }
        player.playWhenReady = true
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        stopTicking()
        _state.value = PlayerUiState()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        _state.value = _state.value.copy(positionMs = positionMs.coerceAtLeast(0))
    }

    override fun release() {
        stopTicking()
        player.release()
        _state.value = PlayerUiState()
    }

    private fun startTicking() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun stopTicking() {
        handler.removeCallbacks(ticker)
    }

    private fun pushPosition() {
        val pos = player.currentPosition.coerceAtLeast(0)
        val playerDuration = player.duration
        val duration = if (playerDuration != C.TIME_UNSET && playerDuration > 0) {
            playerDuration
        } else {
            _state.value.durationMs
        }
        _state.value = _state.value.copy(positionMs = pos, durationMs = duration)
    }

    companion object {
        private const val POSITION_INTERVAL_MS = 250L
    }
}
