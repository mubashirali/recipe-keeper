package com.mobiapps.recipekeep.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

enum class RecState { Idle, Recording, Paused }

/** One recording at a time. Each start()..stop() produces one .m4a clip. */
class AudioRecorder(context: Context) {

    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var accumulatedMs = 0L
    private var segmentStart = 0L

    private val _state = MutableStateFlow(RecState.Idle)
    val state: StateFlow<RecState> = _state.asStateFlow()

    /** Called when the max-duration cap stops the recording. The clip is already finalised. */
    var onAutoStop: ((File) -> Unit)? = null

    fun elapsedMs(): Long =
        accumulatedMs + if (_state.value == RecState.Recording) SystemClock.elapsedRealtime() - segmentStart else 0L

    fun start(): Result<Unit> = runCatching {
        check(_state.value == RecState.Idle) { "Already recording" }
        val out = File(appContext.getExternalFilesDir(null), "recipe_audio_${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000) // ponytail: ~0.5 MB/min, fine for speech
            r.setMaxDuration(MAX_DURATION_MS)
            r.setOutputFile(out.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stop().onSuccess { onAutoStop?.invoke(it) }
                }
            }
            r.prepare()
            r.start()
        } catch (e: Exception) {
            runCatching { r.release() }
            out.delete()
            throw e
        }
        recorder = r
        file = out
        accumulatedMs = 0L
        segmentStart = SystemClock.elapsedRealtime()
        _state.value = RecState.Recording
    }

    fun pause(): Result<Unit> = runCatching {
        val r = checkNotNull(recorder)
        check(_state.value == RecState.Recording)
        r.pause()
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
        _state.value = RecState.Paused
    }

    fun resume(): Result<Unit> = runCatching {
        val r = checkNotNull(recorder)
        check(_state.value == RecState.Paused)
        r.resume()
        segmentStart = SystemClock.elapsedRealtime()
        _state.value = RecState.Recording
    }

    /** Finalises the clip. On failure (e.g. stopped instantly) the partial file is deleted. */
    fun stop(): Result<File> {
        val r = recorder ?: return Result.failure(IllegalStateException("Not recording"))
        val f = checkNotNull(file)
        recorder = null
        file = null
        _state.value = RecState.Idle
        val result = runCatching { r.stop(); f }
        runCatching { r.release() }
        if (result.isFailure) f.delete()
        return result
    }

    /** Stops and throws the clip away. */
    fun cancel() {
        stop().onSuccess { it.delete() }
    }

    private companion object {
        const val MAX_DURATION_MS = 10 * 60 * 1000
    }
}
