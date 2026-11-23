package com.mobiapps.recipekeep.util

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Plays one clip at a time. toggle() = play / pause; a different path switches clip. */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var loadedPath: String? = null

    private val _playing = MutableStateFlow<String?>(null)
    /** Path that is audibly playing right now, or null (paused / stopped). */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    /** @return false when the clip could not be played (missing / unreadable file). */
    fun toggle(path: String): Boolean {
        val current = player
        if (current != null && loadedPath == path) {
            return runCatching {
                if (current.isPlaying) { current.pause(); _playing.value = null }
                else { current.start(); _playing.value = path }
            }.isSuccess
        }
        stop()
        if (!File(path).exists()) return false
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(path)
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
            player = mp
            loadedPath = path
            _playing.value = path
            true
        } catch (e: Exception) {
            runCatching { mp.release() }
            false
        }
    }

    fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
        loadedPath = null
        _playing.value = null
    }
}
