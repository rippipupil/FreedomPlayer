package com.freedomplayer.android.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Reproducción en segundo plano: la notificación con los controles, la pantalla de bloqueo, los
 * botones de los auriculares y el foco de audio. Envuelve el reproductor de [Players] en una
 * sesión de Media3; mientras suena, el servicio sigue vivo aunque se cierre la app.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        session = MediaSession.Builder(this, Players.main(this))
            .setCallback(Callback)
            .apply { if (open != null) setSessionActivity(open) }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        // El reproductor es de la app (sigue con su cola); aquí solo se suelta la sesión.
        session?.release()
        session = null
        super.onDestroy()
    }

    /**
     * La cola y su orden aleatorio los decide la app: los mandos de fuera (notificación, coche,
     * reloj) pueden tocar play, pausa, siguiente, anterior y la barra, pero no cambiar el orden.
     */
    private object Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SET_SHUFFLE_MODE)
                .remove(Player.COMMAND_SET_REPEAT_MODE)
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(commands)
                .build()
        }
    }

    companion object {
        /**
         * Conecta la app con el servicio mientras se ve la pantalla: así el servicio crea la
         * sesión y, cuando empieza a sonar, pone la notificación y pasa a primer plano.
         */
        fun connect(context: Context): ListenableFuture<MediaController> =
            MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                .buildAsync()

        fun disconnect(future: ListenableFuture<MediaController>?) {
            future?.let(MediaController::releaseFuture)
        }
    }
}
