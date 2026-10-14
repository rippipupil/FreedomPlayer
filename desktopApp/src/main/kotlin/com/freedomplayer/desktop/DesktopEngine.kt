package com.freedomplayer.desktop

import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.PreviewSnapshot
import com.freedomplayer.data.StreamSource
import com.freedomplayer.data.Track

/**
 * TODO(PC): el audio de verdad en el PC (FFmpeg con JavaCV, sin instalar nada aparte).
 * Mientras tanto la cola funciona, pero al pulsar play avisa de que en el PC todavía no suena.
 */
class SilentEngine : AudioEngine {
    private var tracks: List<Track> = emptyList()
    private var index = -1
    private var position = 0L
    private var transitions = 0
    private var error: String? = null
    private var errorSerial = 0
    private var previewError: String? = null

    private val message = "En el PC todavía no suena el audio: llega en la próxima versión. En el móvil, sí."

    private fun notYet() {
        error = message
        errorSerial++
    }

    override fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean) {
        this.tracks = tracks
        this.index = if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)
        position = positionMs.coerceAtLeast(0)
        transitions++
        if (play) notYet()
    }

    override fun reorder(tracks: List<Track>, index: Int) {
        this.tracks = tracks
        this.index = if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)
    }

    override fun play() = notYet()

    override fun pause() = Unit

    override fun seekTo(positionMs: Long) {
        position = positionMs.coerceAtLeast(0)
    }

    override fun skipTo(index: Int) {
        if (index !in tracks.indices) return
        this.index = index
        position = 0
        transitions++
    }

    override fun setRepeat(mode: RepeatMode) = Unit

    override fun setVolume(volume: Float) = Unit

    override fun startPreview(source: StreamSource) {
        previewError = message
    }

    override fun previewPlay() = Unit

    override fun previewPause() = Unit

    override fun previewSeek(positionMs: Long) = Unit

    override fun stopPreview() {
        previewError = null
    }

    override fun snapshot(): EngineSnapshot = EngineSnapshot(
        index = index,
        playing = false,
        positionMs = position,
        durationMs = tracks.getOrNull(index)?.durationMs ?: 0L,
        transitions = transitions,
        error = error,
        errorSerial = errorSerial,
        preview = previewError?.let { PreviewSnapshot(false, false, 0, 0, false, it) },
    )
}
