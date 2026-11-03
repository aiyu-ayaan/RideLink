package com.aiyu.ridelink.service

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.aiyu.ridelink.core.ClusterController
import com.aiyu.ridelink.core.protocol.ClusterProtocol

/**
 * Follows the active media session (Spotify, YouTube Music, ...) and reports it to the controller.
 * Reading other apps' sessions needs the notification listener grant, so the listener owns this.
 */
class MusicWatcher(private val context: Context, private val listener: ComponentName, private val controller: ClusterController) {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var current: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = report()
        override fun onPlaybackStateChanged(state: PlaybackState?) = report()
        override fun onSessionDestroyed() = pick(emptyList())
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    fun start() {
        runCatching {
            sessions?.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
            pick(sessions?.getActiveSessions(listener).orEmpty())
        }
    }

    fun stop() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        current?.unregisterCallback(callback)
        current = null
    }

    /** Prefer the session that is playing; otherwise the most recent one. */
    private fun pick(list: List<MediaController>) {
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (next?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = next
            next?.registerCallback(callback, main)
        }
        report()
    }

    private fun report() {
        val c = current
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || title.isNullOrBlank()) {
            controller.onMusic(null)
            return
        }
        val playback = c.playbackState
        val state = when (playback?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> ClusterProtocol.PlayState.PLAYING
            PlaybackState.STATE_PAUSED -> ClusterProtocol.PlayState.PAUSED
            else -> ClusterProtocol.PlayState.STOPPED
        }
        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.takeIf { it > 0 } ?: 1
        val vol = (audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) * 100 / max
        controller.onMusic(
            ClusterProtocol.Music(
                title = title,
                artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                state = state,
                positionMs = playback?.position ?: 0,
                durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                volumePercent = vol,
            ),
        )
    }
}
