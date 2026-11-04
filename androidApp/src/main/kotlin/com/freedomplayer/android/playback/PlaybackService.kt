package com.freedomplayer.android.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.freedomplayer.android.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Reproducción en segundo plano: la notificación con los controles (con el tema de la app: ver
 * [ThemedArtwork]), la pantalla de bloqueo, los botones de los auriculares y el foco de audio. Envuelve el reproductor de [Players] en una
 * sesión de Media3; mientras suena, el servicio sigue vivo aunque se cierre la app.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    /** El botón de repetir cambia de icono cuando cambia el modo (desde la notificación o desde la app). */
    private val repeatListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            session?.setMediaButtonPreferences(listOf(repeatButton(repeatMode)))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        // Notificación con el tema: el fantasma como icono pequeño y el canal en español.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_ghost) },
        )
        val player = Players.main(this)
        player.addListener(repeatListener)
        session = MediaSession.Builder(this, player)
            .setCallback(Callback)
            // Repetir (seguir ⇄ una), al lado de los mandos de siempre.
            .setMediaButtonPreferences(listOf(repeatButton(player.repeatMode)))
            // Las carátulas llegan al sistema montadas en el tema (ver ThemedArtwork).
            .setBitmapLoader(ThemedBitmapLoader(DataSourceBitmapLoader(this)))
            .apply { if (open != null) setSessionActivity(open) }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        // El reproductor es de la app (sigue con su cola); aquí solo se suelta la sesión.
        session?.let {
            it.player.removeListener(repeatListener)
            it.release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * La cola y su orden aleatorio los decide la app: los mandos de fuera (notificación, coche,
     * reloj) pueden tocar play, pausa, siguiente, anterior y la barra, pero no cambiar el orden.
     * Repetir va con un botón propio ([REPEAT]) que cambia el modo en el orden de la app; la app
     * lo ve en el siguiente vistazo al reproductor (AndroidEngine.snapshot).
     */
    private object Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SET_SHUFFLE_MODE)
                .remove(Player.COMMAND_SET_REPEAT_MODE)
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                .build()
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(REPEAT)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(commands)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != REPEAT.customAction) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            val player = session.player
            player.repeatMode = nextRepeat(player.repeatMode)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        /** El botón de repetir de la notificación (un mando propio de la sesión). */
        const val REPEAT_ACTION = "com.freedomplayer.REPEAT"
        private val REPEAT = SessionCommand(REPEAT_ACTION, Bundle.EMPTY)

        /**
         * El mismo orden que el botón de la app (RepeatMode.next): seguir ⇄ repetir una. La cola
         * nunca se para, así que «no repetir» pasa a «seguir».
         */
        fun nextRepeat(mode: Int): Int = if (mode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE

        /** El botón de repetir con el icono del modo actual y lo que hace al tocarlo. */
        private fun repeatButton(mode: Int): CommandButton {
            val (icon, name) = when (mode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE to "Repetir esta canción (toca para seguir con la lista)"
                else -> CommandButton.ICON_REPEAT_ALL to "Seguir con la lista (toca para repetir esta canción)"
            }
            return CommandButton.Builder(icon)
                .setDisplayName(name)
                .setSessionCommand(REPEAT)
                .build()
        }

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
