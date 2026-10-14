package com.freedomplayer.android

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freedomplayer.android.AudioFiles.onMain
import com.freedomplayer.android.AudioFiles.track
import com.freedomplayer.android.AudioFiles.wav
import com.freedomplayer.android.playback.AndroidEngine
import com.freedomplayer.android.playback.PlaybackService
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.EngineSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Los mandos de fuera de la app (notificación, pantalla de bloqueo, auriculares) llegan por la
 * sesión de Media3 al mismo reproductor que usa la app, y la notificación enseña lo que suena.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var engine: AndroidEngine

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "pruebas-sesion").apply {
            deleteRecursively()
            mkdirs()
        }
        engine = onMain { AndroidEngine(context) }
    }

    @After
    fun tearDown() {
        onMain {
            engine.load(emptyList(), 0, 0, play = false)
            engine.release()
        }
        dir.deleteRecursively()
    }

    private fun waitFor(what: String, check: (EngineSnapshot) -> Boolean): EngineSnapshot =
        AudioFiles.waitFor(what, 20_000, { engine.snapshot() }, check)

    private fun notificationTitles(): List<String> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .mapNotNull { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() }

    @Test
    fun outsideControlsDriveTheAppPlayerAndTheNotificationShowsTheSong() {
        val tracks = (1..2).map { track("$it", wav(dir, "$it.wav", 8.0)) }
        val future = onMain { PlaybackService.connect(context) }
        val controller = future.get(20, TimeUnit.SECONDS)
        try {
            onMain { engine.load(tracks, 0, 0, play = true) }
            AudioFiles.waitFor("que el mando vea la canción", 20_000, {
                controller.isPlaying to controller.mediaMetadata.title?.toString()
            }) { it == (true to "Canción 1") }

            // Fuera de la app se puede pausar, seguir y pasar de canción, pero no cambiar la cola.
            val commands = onMain { controller.availableCommands }
            assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
            assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
            assertFalse(commands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
            assertFalse(commands.contains(Player.COMMAND_SET_REPEAT_MODE))
            assertFalse(commands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))

            onMain { controller.pause() }
            waitFor("que la pausa de fuera llegue a la app") { !it.playing }
            onMain { controller.play() }
            waitFor("y el play") { it.playing }

            val before = onMain { engine.snapshot() }
            onMain { controller.seekToNext() }
            val next = waitFor("la siguiente, pedida desde fuera") { it.index == 1 }
            assertEquals("la app se entera del cambio de canción", before.transitions + 1, next.transitions)
            assertTrue(next.playing)

            AudioFiles.waitFor("la notificación con «Canción 2»", 20_000, { notificationTitles() }) { "Canción 2" in it }
        } finally {
            onMain { MediaController.releaseFuture(future) }
        }
    }

    @Test
    fun theRepeatButtonOfTheNotificationCyclesLikeTheApp() {
        val tracks = (1..2).map { track("$it", wav(dir, "$it.wav", 8.0)) }
        val future = onMain { PlaybackService.connect(context) }
        val controller = future.get(20, TimeUnit.SECONDS)
        try {
            onMain {
                engine.setRepeat(RepeatMode.Off)
                engine.load(tracks, 0, 0, play = false)
            }
            val repeat = SessionCommand(PlaybackService.REPEAT_ACTION, Bundle.EMPTY)
            AudioFiles.waitFor("el botón de repetir en la sesión", 20_000, {
                controller.mediaButtonPreferences.mapNotNull { it.sessionCommand?.customAction }
            }) { PlaybackService.REPEAT_ACTION in it }
            assertTrue(onMain { controller.isSessionCommandAvailable(repeat) })

            for (expected in listOf(RepeatMode.All, RepeatMode.One, RepeatMode.Off)) {
                onMain { controller.sendCustomCommand(repeat, Bundle.EMPTY) }.get(10, TimeUnit.SECONDS)
                waitFor("repetir: $expected") { it.repeat == expected }
            }
        } finally {
            onMain { MediaController.releaseFuture(future) }
        }
    }
}
