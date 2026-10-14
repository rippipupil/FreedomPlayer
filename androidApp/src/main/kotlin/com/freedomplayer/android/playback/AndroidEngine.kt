package com.freedomplayer.android.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.freedomplayer.core.Crossfade
import com.freedomplayer.core.EqBands
import com.freedomplayer.core.Loudness
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.PreviewSnapshot
import com.freedomplayer.data.SoundSettings
import com.freedomplayer.data.StreamSource
import com.freedomplayer.data.Track
import java.io.File
import kotlin.math.abs
import android.media.audiofx.Equalizer as SystemEqualizer

/**
 * El audio de verdad en Android, con Media3 (ExoPlayer). La app decide la cola (y su orden
 * aleatorio) y aquí se toca tal cual; la notificación y la pantalla de bloqueo controlan este
 * mismo reproductor a través de [PlaybackService]. Todo se llama desde el hilo principal.
 *
 * El ecualizador es el del sistema (android.media.audiofx) sobre la sesión de audio del
 * reproductor; el volumen igualado baja el volumen del reproductor en las canciones fuertes; y
 * el fundido usa un segundo reproductor con la canción que se va, mientras el principal (el de la
 * notificación) ya va por la siguiente, subiendo.
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

    private var sound = SoundSettings()

    /** Volumen igualado (dB) de cada canción de la cola, por id. */
    private val gains = HashMap<String, Float>()

    private var systemEq: SystemEqualizer? = null
    private var eqSession = C.AUDIO_SESSION_ID_UNSET

    /** El ecualizador del sistema no está o no deja usarse (pasa en algunos móviles). */
    private var eqAvailable = true

    override val equalizerAvailable: Boolean get() = eqAvailable

    // ---------- Fundido ----------

    private val handler = Handler(Looper.getMainLooper())
    private var released = false

    /** Preparada (en pausa) en el punto donde empezará el fundido de la que suena. */
    private var standby: ExoPlayer? = null
    private var standbyId: String? = null

    /** Una canción que no llegó a tiempo al fundido: acaba normal. */
    private var noFadeId: String? = null

    /** La que se va apagando. */
    private var tail: ExoPlayer? = null
    private var tailGain = 1f
    private var fadeStart = 0L
    private var fadeLength = 0L

    /** Hay un fundido en marcha (para las pruebas). */
    val crossfading: Boolean get() = tail != null

    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            crossfadeStep()
            handler.postDelayed(this, if (tail != null || standby != null) 20L else 200L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Cambiar la cola entera ya cuenta en load().
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) transitions++
            noFadeId = null
            // Durante un fundido el volumen lo lleva crossfadeStep().
            if (tail == null) applyVolume()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            applyEq()
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
        handler.post(tick)
    }

    private fun rememberGains(tracks: List<Track>) {
        gains.clear()
        for (t in tracks) gains[t.id] = t.gainDb
    }

    private fun gainFor(id: String?): Float =
        if (!sound.normalize || id == null) 1f else Loudness.linear(gains[id] ?: 0f).coerceIn(0f, 1f)

    private fun applyVolume() {
        player.volume = gainFor(player.currentMediaItem?.mediaId)
    }

    override fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean) {
        cancelCrossfade()
        rememberGains(tracks)
        ended = false
        transitions++
        if (tracks.isEmpty()) {
            player.clearMediaItems()
            player.stop()
            return
        }
        player.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.lastIndex), positionMs.coerceAtLeast(0))
        applyVolume()
        // Al abrir la app (sin play) no se prepara: así no sale la notificación hasta que suene algo.
        if (play) {
            player.prepare()
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }

    override fun reorder(tracks: List<Track>, index: Int) {
        rememberGains(tracks)
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
        cancelCrossfade()
        player.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        if (player.mediaItemCount == 0) return
        cancelCrossfade()
        ended = false
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun skipTo(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        cancelCrossfade()
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
        if (on) dropStandby()
    }

    override fun setSound(sound: SoundSettings) {
        val before = this.sound
        this.sound = sound
        if (before.eqGains != sound.eqGains) applyEq()
        if (before.normalize != sound.normalize && tail == null) applyVolume()
        if (sound.crossfadeMs <= 0) dropStandby()
    }

    /** Las 5 bandas de la app sobre las del ecualizador del sistema (suelen ser las mismas 5). */
    private fun applyEq() {
        val wanted = sound.eqGains
        val flat = wanted.all { abs(it) < 0.05f }
        if (flat && systemEq == null) return
        try {
            val session = player.audioSessionId
            var e = systemEq
            if (e == null || eqSession != session) {
                e?.release()
                e = SystemEqualizer(0, session)
                systemEq = e
                eqSession = session
            }
            if (flat) {
                e.setEnabled(false)
            } else {
                val range = e.bandLevelRange
                for (band in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(band.toShort()) / 1000f
                    val level = (EqBands.gainAt(wanted, hz) * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(band.toShort(), level.toShort())
                }
                e.setEnabled(true)
            }
            eqAvailable = true
        } catch (t: Throwable) {
            // Sin ecualizador del sistema: la app lo dice en la hoja del ecualizador.
            eqAvailable = false
            runCatching { systemEq?.release() }
            systemEq = null
        }
    }

    /** Lo que tiene puesto el ecualizador del sistema (para las pruebas): dB de cada banda, o null si está apagado. */
    fun systemEqLevels(): List<Float>? {
        val e = systemEq ?: return null
        if (!e.enabled) return null
        return (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()) / 100f }
    }

    // ---------- Fundido ----------

    private fun dropStandby() {
        standby?.release()
        standby = null
        standbyId = null
    }

    private fun endCrossfade() {
        val t = tail ?: return
        tail = null
        t.release()
        applyVolume()
    }

    private fun cancelCrossfade() {
        endCrossfade()
        dropStandby()
    }

    /**
     * Cada 200 ms (20 ms cerca del fundido): unos segundos antes se prepara la canción que suena
     * en otro reproductor, en pausa en el punto del fundido; al llegar, ese suena y se va
     * apagando, y el principal pasa a la siguiente y va subiendo.
     */
    private fun crossfadeStep() {
        val t = tail
        if (t != null) {
            val stopped = !player.playWhenReady || player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
            val p = ((SystemClock.uptimeMillis() - fadeStart).toFloat() / fadeLength).coerceIn(0f, 1f)
            if (stopped || p >= 1f || t.playbackState == Player.STATE_ENDED) {
                endCrossfade()
                return
            }
            player.volume = gainFor(player.currentMediaItem?.mediaId) * Crossfade.fadeIn(p)
            t.volume = tailGain * Crossfade.fadeOut(p)
            return
        }
        val len = sound.crossfadeMs
        val item = player.currentMediaItem
        val total = player.duration
        val ready = len > 0 && item != null && player.playWhenReady && player.playbackState == Player.STATE_READY &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            !player.pauseAtEndOfMediaItems && player.repeatMode != Player.REPEAT_MODE_ONE &&
            player.hasNextMediaItem() && total != C.TIME_UNSET && total >= len * 2 && item.mediaId != noFadeId
        if (!ready || item == null) {
            dropStandby()
            return
        }
        val at = total - len
        val position = player.currentPosition
        if (position < at - 3_000) {
            dropStandby()
            return
        }
        var s = standby
        if (s == null || standbyId != item.mediaId) {
            dropStandby()
            s = Players.tail(app, player.audioSessionId)
            s.setMediaItem(item)
            s.prepare()
            s.seekTo(at)
            s.volume = gainFor(item.mediaId)
            s.playWhenReady = false
            standby = s
            standbyId = item.mediaId
        }
        if (position < at) return
        if (s.playbackState != Player.STATE_READY) {
            // No ha llegado a tiempo: esta acaba sin fundido.
            if (position > at + 500) {
                noFadeId = item.mediaId
                dropStandby()
            }
            return
        }
        // Empieza: la que se va sigue en el otro reproductor y la siguiente entra en silencio.
        standby = null
        standbyId = null
        tail = s
        tailGain = s.volume
        fadeLength = len
        fadeStart = SystemClock.uptimeMillis()
        s.playWhenReady = true
        player.volume = 0f
        player.seekToNextMediaItem()
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
            // Se puede cambiar desde la notificación (ver PlaybackService).
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                else -> RepeatMode.Off
            },
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
        released = true
        handler.removeCallbacks(tick)
        cancelCrossfade()
        runCatching { systemEq?.release() }
        systemEq = null
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
                    // Siempre una imagen: sin carátula, la del tema (ThemedBitmapLoader la pinta).
                    .setArtworkUri(artworkUri?.let(::uriOf) ?: ThemedArtwork.PLACEHOLDER)
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
