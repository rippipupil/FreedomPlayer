package com.freedomplayer.android.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Los reproductores de la app. El principal es uno para todo el proceso: lo usan la app (que
 * decide la cola) y [PlaybackService] (notificación, pantalla de bloqueo y auriculares).
 */
@OptIn(UnstableApi::class)
object Players {
    private var main: ExoPlayer? = null

    private val musicAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    /** Pide el foco de audio (se calla si entra una llamada) y se pausa al quitar los auriculares. */
    fun main(context: Context): ExoPlayer = main ?: ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(musicAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
        .also { main = it }

    /**
     * La que se va apagando durante un fundido entre canciones: sin foco de audio propio (lo
     * lleva el principal) y en la misma sesión de audio, para que le llegue el ecualizador.
     */
    fun tail(context: Context, audioSessionId: Int): ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(musicAttributes, false)
        .build()
        .also { if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) it.setAudioSessionId(audioSessionId) }

    /**
     * Un reproductor aparte para la vista previa (escuchar un resultado sin descargarlo), con las
     * cabeceras que pide YouTube para esa dirección. La cola principal se queda donde estaba.
     */
    fun preview(context: Context, headers: Map<String, String>): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"])
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val app = context.applicationContext
        return ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http)))
            .setAudioAttributes(musicAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
