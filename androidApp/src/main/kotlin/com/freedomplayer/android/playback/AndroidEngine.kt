package com.freedomplayer.android.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.PreviewSnapshot
import com.freedomplayer.data.StreamSource
import com.freedomplayer.data.Track
import java.io.File

/**
 * El audio de verdad en Android, con Media3 (ExoPlayer). La app decide la cola (y su orden
 * aleatorio) y aquí se toca tal cual; la notificación y la pantalla de bloqueo controlan este
 * mismo reproductor a través de [PlaybackService]. Todo se llama desde el hilo principal.
 */
@OptIn(UnstableApi::class)
class AndroidEngine(context: Context) : AudioEngine {
    private val app = context.applicationContext
    private val player: ExoPlayer = Players.main(app)

    private var transitions = 0
    private var error: String? = null
    private var errorSerial = 0
    private var ended = false
    private var failedInARow = 0

    private var preview: ExoPlayer? = null
    private var previewError: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Cambiar la cola entera ya cuenta en load().
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) transitions++
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> failedInARow = 0
                Player.STATE_ENDED -> {
                    // Fin de la cola sin repetir: se para al principio de la última.
                    ended = true
                    player.playWhenReady = false
                    player.seekTo(player.currentMediaItemIndex, 0)
                }
                else -> Unit
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            val title = player.currentMediaItem?.mediaMetadata?.title ?: "la canción"
            error = "No se pudo reproducir «$title»: ${describe(e)}."
            errorSerial++
            failedInARow++
            // Si una falla (un archivo borrado…), se sigue con la siguiente.
            if (player.hasNextMediaItem() && failedInARow < player.mediaItemCount) {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

    init {
        player.addListener(listener)
        // El reproductor es del proceso: lo que dejase puesto una app anterior no vale.
        player.pauseAtEndOfMediaItems = false
    }

    override fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean) {
        ended = false
        transitions++
        if (tracks.isEmpty()) {
            player.clearMediaItems()
            player.stop()
            return
        }
        player.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.lastIndex), positionMs.coerceAtLeast(0))
        // Al abrir la app (sin play) no se prepara: así no sale la notificación hasta que suene algo.
        if (play) {
            player.prepare()
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }

    override fun reorder(tracks: List<Track>, index: Int) {
        val count = player.mediaItemCount
        if (count == 0 || tracks.isEmpty()) {
            load(tracks, index, 0, play = false)
            return
        }
        // La que suena se queda (sin cortes); las demás se cambian por el orden nuevo.
        val current = player.currentMediaItemIndex
        if (current + 1 < count) player.removeMediaItems(current + 1, count)
        if (current > 0) player.removeMediaItems(0, current)
        val i = index.coerceIn(0, tracks.lastIndex)
        if (i > 0) player.addMediaItems(0, tracks.subList(0, i).map { it.toMediaItem() })
        if (i < tracks.lastIndex) player.addMediaItems(tracks.subList(i + 1, tracks.size).map { it.toMediaItem() })
    }

    override fun play() {
        if (player.mediaItemCount == 0) return
        if (ended || player.playbackState == Player.STATE_ENDED) {
            ended = false
            player.seekTo(player.currentMediaItemIndex, 0)
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        if (player.mediaItemCount == 0) return
        ended = false
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun skipTo(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        ended = false
        player.seekTo(index, 0)
        if (player.playbackState == Player.STATE_IDLE && player.playWhenReady) player.prepare()
    }

    override fun setRepeat(mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.All -> Player.REPEAT_MODE_ALL
            RepeatMode.One -> Player.REPEAT_MODE_ONE
        }
    }

    /** En el móvil manda el volumen del sistema. */
    override fun setVolume(volume: Float) = Unit

    /** ExoPlayer ya sabe pararse al final de la canción (y con play sigue con la siguiente). */
    override fun setPauseAtEndOfSong(on: Boolean) {
        player.pauseAtEndOfMediaItems = on
    }

    // ---------- Vista previa ----------

    override fun startPreview(source: StreamSource) {
        stopPreview()
        val p = Players.preview(app, source.headers)
        p.addListener(
            object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    previewError = describe(e).replaceFirstChar { it.uppercase() } + "."
                }
            },
        )
        p.setMediaItem(MediaItem.fromUri(source.url))
        p.prepare()
        p.playWhenReady = true
        preview = p
    }

    override fun previewPlay() {
        val p = preview ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        p.playWhenReady = true
    }

    override fun previewPause() {
        preview?.playWhenReady = false
    }

    override fun previewSeek(positionMs: Long) {
        preview?.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun stopPreview() {
        preview?.release()
        preview = null
        previewError = null
    }

    // ---------- Estado ----------

    override fun snapshot(): EngineSnapshot {
        val state = player.playbackState
        val empty = player.mediaItemCount == 0
        return EngineSnapshot(
            index = if (empty) -1 else player.currentMediaItemIndex,
            // Lo que el usuario ha pedido (sonar o no); durante una llamada, en pausa.
            playing = player.playWhenReady &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                (state == Player.STATE_READY || state == Player.STATE_BUFFERING),
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
            ended = ended,
            transitions = transitions,
            error = error,
            errorSerial = errorSerial,
            preview = preview?.let { p ->
                val ps = p.playbackState
                PreviewSnapshot(
                    playing = p.playWhenReady && ps != Player.STATE_ENDED,
                    loading = ps == Player.STATE_BUFFERING || (ps == Player.STATE_IDLE && previewError == null),
                    positionMs = p.currentPosition.coerceAtLeast(0),
                    durationMs = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                    ended = ps == Player.STATE_ENDED,
                    error = previewError,
                )
            },
        )
    }

    /** El reproductor es del proceso (lo comparte la sesión): aquí solo se deja de escucharlo. */
    override fun release() {
        stopPreview()
        player.removeListener(listener)
    }

    companion object {
        private fun uriOf(path: String): Uri =
            if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)

        fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setUri(uriOf(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri?.let(::uriOf))
                    .build(),
            )
            .build()

        /** El error, dicho para el aviso. */
        fun describe(e: PlaybackException): String = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "el archivo ya no está"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "no hay permiso para leer el archivo"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "no hay conexión"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "YouTube no deja abrirlo ahora (prueba otra vez)"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> "el formato del archivo no se puede reproducir"
            else -> PlaybackException.getErrorCodeName(e.errorCode)
        }
    }
}
