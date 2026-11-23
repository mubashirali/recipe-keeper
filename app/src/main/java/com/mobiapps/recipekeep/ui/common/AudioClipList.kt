package com.mobiapps.recipekeep.ui.common

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.mobiapps.recipekeep.databinding.ItemAudioClipBinding
import com.mobiapps.recipekeep.util.AudioPlayer

/** Renders one row per clip into [container]. Pass [onDelete] to show delete buttons (creator only). */
class AudioClipList(
    private val container: LinearLayout,
    private val player: AudioPlayer,
    private val onDelete: ((String) -> Unit)? = null,
    private val onMissing: () -> Unit
) {
    fun render(paths: List<String>, playing: String?) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        paths.forEachIndexed { index, path ->
            val row = ItemAudioClipBinding.inflate(inflater, container, true)
            row.tvClipName.text = "Voice note ${index + 1}"
            row.btnPlay.setIconResource(
                if (path == playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            row.btnPlay.setOnClickListener { if (!player.toggle(path)) onMissing() }
            row.btnDeleteClip.isVisible = onDelete != null
            row.btnDeleteClip.setOnClickListener { onDelete?.invoke(path) }
        }
    }
}
