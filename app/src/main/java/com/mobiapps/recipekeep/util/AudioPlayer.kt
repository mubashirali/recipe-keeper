package com.mobiapps.recipekeep.util

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Plays one clip at a time. toggle() = play / pause; a different path switches clip. Main-thread only. */
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
            return try {
                if (current.isPlaying) { current.pause(); _playing.value = null }
                else { current.start(); _playing.value = path }
                true
            } catch (e: Exception) {
                Log.w("AudioPlayer", "pause/start failed for $path", e)
                stop()
                false
            }
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
            Log.w("AudioPlayer", "could not play $path", e)
            runCatching { mp.release() }
            false
        }
    }

    /** Stops playback if [path] is the loaded clip (playing or paused). */
    fun stopIf(path: String) { if (loadedPath == path) stop() }

    fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
        loadedPath = null
        _playing.value = null
    }
}
