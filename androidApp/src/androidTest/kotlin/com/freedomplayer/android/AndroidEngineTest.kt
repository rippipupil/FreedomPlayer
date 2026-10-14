package com.freedomplayer.android

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freedomplayer.android.AudioFiles.onMain
import com.freedomplayer.android.AudioFiles.track
import com.freedomplayer.android.AudioFiles.wav
import com.freedomplayer.android.playback.AndroidEngine
import com.freedomplayer.android.playback.Players
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.SoundSettings
import com.freedomplayer.data.StreamSource
import com.freedomplayer.platform.AndroidLoudness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * El audio de verdad en Android (ExoPlayer, el mismo reproductor que usa la notificación), con
 * archivos WAV hechos en la prueba: la cola que sigue y se para al final, repetir, un archivo que
 * falta, el orden aleatorio sin cortes, la vista previa, el temporizador, el fundido, el volumen
 * igualado y el ecualizador del sistema.
 */
@RunWith(AndroidJUnit4::class)
class AndroidEngineTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var engine: AndroidEngine

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "pruebas-audio").apply {
            deleteRecursively()
            mkdirs()
        }
        engine = onMain { AndroidEngine(context) }
    }

    @After
    fun tearDown() {
        onMain {
            engine.setRepeat(RepeatMode.Off)
            engine.setPauseAtEndOfSong(false)
            engine.setSound(SoundSettings())
            engine.load(emptyList(), 0, 0, play = false)
            engine.release()
            Players.main(context).volume = 1f
        }
        dir.deleteRecursively()
    }

    private fun snapshot(): EngineSnapshot = onMain { engine.snapshot() }

    private fun waitFor(what: String, timeoutMs: Long = 20_000, check: (EngineSnapshot) -> Boolean): EngineSnapshot =
        AudioFiles.waitFor(what, timeoutMs, { engine.snapshot() }, check)

    private fun queueIds(): List<String> = onMain {
        val p = Players.main(context)
        (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
    }

    @Test
    fun theQueueFollowsOnAndStopsAtTheEnd() {
        val tracks = (1..3).map { track("$it", wav(dir, "$it.wav", 0.7)) }
        onMain { engine.load(tracks, 0, 0, play = true) }
        val start = snapshot().transitions
        waitFor("que suene") { it.playing }
        waitFor("la tercera") { it.index == 2 }
        val end = waitFor("el final de la cola") { it.ended }
        assertEquals(2, end.index)
        assertFalse("al final se para", end.playing)
        assertEquals("dos cambios de canción", start + 2, end.transitions)
        waitFor("vuelve al principio de la última") { it.positionMs < 100 }

        // Play otra vez: vuelve a sonar la última desde el principio.
        onMain { engine.play() }
        val again = waitFor("que vuelva a sonar") { it.playing }
        assertFalse(again.ended)
        assertEquals(2, again.index)
    }

    @Test
    fun repeatOneAndRepeatAll() {
        val tracks = (1..2).map { track("$it", wav(dir, "$it.wav", 0.6)) }
        onMain {
            engine.setRepeat(RepeatMode.One)
            engine.load(tracks, 1, 0, play = true)
        }
        val start = snapshot().transitions
        val repeated = waitFor("que se repita dos veces") { it.transitions >= start + 2 }
        assertEquals("repetir una: siempre la misma", 1, repeated.index)

        onMain { engine.setRepeat(RepeatMode.All) }
        waitFor("que vuelva a la primera") { it.index == 0 }
        assertTrue(snapshot().playing)
        assertFalse(snapshot().ended)
    }

    @Test
    fun aMissingFileIsReportedAndTheNextOnePlays() {
        val tracks = listOf(track("x", File(dir, "no-existe.webm")), track("2", wav(dir, "2.wav", 2.0)))
        onMain { engine.load(tracks, 0, 0, play = true) }
        val failed = waitFor("el aviso del error") { it.errorSerial > 0 }
        assertNotNull(failed.error)
        assertTrue(failed.error!!, failed.error!!.contains("Canción x"))
        waitFor("que suene la siguiente") { it.index == 1 && it.playing && it.positionMs > 100 }
    }

    @Test
    fun pauseSeekAndSkip() {
        val tracks = (1..2).map { track("$it", wav(dir, "$it.wav", 4.0)) }
        onMain { engine.load(tracks, 0, 0, play = true) }
        waitFor("que avance") { it.positionMs > 300 }
        onMain { engine.pause() }
        val paused = snapshot()
        assertFalse(paused.playing)
        Thread.sleep(400)
        assertTrue("en pausa no avanza", abs(snapshot().positionMs - paused.positionMs) < 100)

        onMain { engine.seekTo(3_000) }
        waitFor("ir al segundo 3") { abs(it.positionMs - 3_000) < 200 }
        onMain { engine.play() }
        waitFor("que vuelva a sonar") { it.playing }
        onMain { engine.skipTo(1) }
        val second = waitFor("la segunda") { it.index == 1 }
        assertTrue("la segunda empieza desde el principio (${second.positionMs})", second.positionMs < 1_000)
    }

    @Test
    fun shuffleReordersWithoutCuttingTheSong() {
        val tracks = (1..4).map { track("$it", wav(dir, "$it.wav", 3.0)) }
        onMain { engine.load(tracks, 1, 0, play = true) }
        waitFor("que suene la segunda") { it.index == 1 && it.positionMs > 300 }
        val before = snapshot()

        // Aleatorio: la que suena (la 2) se queda y las demás cambian de orden.
        val shuffled = listOf(tracks[3], tracks[1], tracks[0], tracks[2])
        onMain { engine.reorder(shuffled, 1) }
        val after = snapshot()
        assertEquals(listOf("4", "2", "1", "3"), queueIds())
        assertEquals(1, after.index)
        assertEquals("sin cortes: no cambia de canción", before.transitions, after.transitions)
        assertTrue("sigue por donde iba", after.positionMs >= before.positionMs)
        assertTrue(after.playing)

        // Quitar el aleatorio: vuelve el orden de la lista, con la misma sonando.
        onMain { engine.reorder(tracks, 1) }
        assertEquals(listOf("1", "2", "3", "4"), queueIds())
        assertEquals(1, snapshot().index)
        assertEquals(before.transitions, snapshot().transitions)

        // Y la siguiente es la que toca en el orden nuevo.
        onMain { engine.reorder(shuffled, 1) }
        onMain { engine.skipTo(2) }
        waitFor("la siguiente del orden aleatorio") { it.index == 2 }
        assertEquals("1", onMain { Players.main(context).currentMediaItem?.mediaId })
    }

    @Test
    fun theSleepTimerCanStopAtTheEndOfTheSong() {
        val tracks = (1..2).map { track("$it", wav(dir, "$it.wav", 1.0)) }
        onMain {
            engine.setPauseAtEndOfSong(true)
            engine.load(tracks, 0, 0, play = true)
        }
        waitFor("que suene") { it.playing }
        val stopped = waitFor("que se pare al final de la primera") { !it.playing }
        assertEquals("no pasa a la siguiente", 0, stopped.index)
        assertTrue("se queda al final (${stopped.positionMs})", stopped.positionMs >= 800)

        // Con play sigue la siguiente (y ya sin temporizador).
        onMain {
            engine.setPauseAtEndOfSong(false)
            engine.play()
        }
        waitFor("la segunda") { it.index == 1 && it.playing }
        waitFor("el final de la cola") { it.ended }
    }

    @Test
    fun previewPausesResumesAndLeavesTheQueueAlone() {
        val song = track("1", wav(dir, "1.wav", 3.0))
        val clip = wav(dir, "vista-previa.wav", 2.0)
        onMain { engine.load(listOf(song), 0, 0, play = true) }
        waitFor("que suene la cola") { it.playing && it.positionMs > 200 }

        // Como hace la app: se pausa la cola y empieza la vista previa.
        onMain {
            engine.pause()
            engine.startPreview(StreamSource(clip.absolutePath, durationSec = 2))
        }
        val queuePosition = snapshot().positionMs
        waitFor("que suene la vista previa") { (it.preview?.positionMs ?: 0) > 200 && it.preview?.playing == true }
        onMain { engine.previewPause() }
        val paused = snapshot().preview!!
        assertFalse(paused.playing)
        Thread.sleep(400)
        assertTrue("la vista previa en pausa no avanza", abs(snapshot().preview!!.positionMs - paused.positionMs) < 100)

        onMain { engine.previewPlay() }
        val ended = waitFor("el final de la vista previa") { it.preview?.ended == true }
        assertTrue("dura unos 2 s (${ended.preview!!.durationMs})", abs(ended.preview!!.durationMs - 2_000) < 200)
        assertNull(ended.preview!!.error)
        assertFalse("la cola sigue en pausa", ended.playing)
        assertTrue("y donde estaba", abs(ended.positionMs - queuePosition) < 100)

        // Al cerrarla vuelve lo que sonaba.
        onMain {
            engine.stopPreview()
            engine.play()
        }
        assertNull(snapshot().preview)
        waitFor("la cola vuelve a sonar") { it.playing && it.positionMs > queuePosition + 200 }
    }

    @Test
    fun crossfadeOverlapsTheEndAndTheStart() {
        val tracks = (1..2).map { track("$it", wav(dir, "$it.wav", 6.0)) }
        onMain {
            engine.setSound(SoundSettings(crossfadeMs = 2_000))
            engine.load(tracks, 0, 0, play = true)
        }
        val t0 = SystemClock.uptimeMillis()
        val start = snapshot().transitions
        val switched = waitFor("que entre la segunda antes de que acabe la primera") { it.index == 1 }
        val elapsed = SystemClock.uptimeMillis() - t0
        val fading = onMain { engine.crossfading }
        val volume = onMain { Players.main(context).volume }
        assertTrue("las dos suenan a la vez", fading)
        assertTrue("la que entra empieza bajita ($volume)", volume < 0.6f)
        assertEquals("cuenta como un cambio de canción", start + 1, switched.transitions)
        assertTrue("entra unos 2 s antes del final de la primera, que dura 6 ($elapsed ms)", elapsed in 3_000L..5_500L)
        AudioFiles.waitFor("el final del fundido", 10_000, { engine.crossfading }) { !it }
        assertEquals(1f, onMain { Players.main(context).volume }, 0.01f)
        assertTrue(snapshot().playing)
        assertEquals(1, snapshot().index)
    }

    @Test
    fun loudSongsAreTurnedDown() {
        val loud = track("1", wav(dir, "1.wav", 3.0)).copy(gainDb = -6f)
        val normal = track("2", wav(dir, "2.wav", 3.0))
        onMain { engine.load(listOf(loud, normal), 0, 0, play = true) }
        waitFor("que suene") { it.playing }
        assertEquals("−6 dB: a la mitad", 0.5f, onMain { Players.main(context).volume }, 0.02f)
        onMain { engine.setSound(SoundSettings(normalize = false)) }
        assertEquals("sin volumen igualado, tal cual", 1f, onMain { Players.main(context).volume }, 0.01f)
        onMain {
            engine.setSound(SoundSettings(normalize = true))
            engine.skipTo(1)
        }
        waitFor("la segunda") { it.index == 1 }
        assertEquals(1f, onMain { Players.main(context).volume }, 0.01f)
    }

    @Test
    fun theSystemEqualizerGetsTheBands() {
        onMain { engine.load(listOf(track("1", wav(dir, "1.wav", 3.0))), 0, 0, play = true) }
        waitFor("que suene") { it.playing }
        onMain { engine.setSound(SoundSettings(eqGains = listOf(6f, 3f, 0f, -3f, -6f))) }
        val levels = onMain { engine.systemEqLevels() }
        if (onMain { engine.equalizerAvailable }) {
            assertNotNull("ecualizador puesto", levels)
            assertTrue("graves arriba y agudos abajo: $levels", levels!!.first() > 2f && levels.last() < -2f)
            onMain { engine.setSound(SoundSettings()) }
            assertNull("plano: apagado", onMain { engine.systemEqLevels() })
        } else {
            // Sin ecualizador del sistema la app lo dice (y no se queda a medias).
            assertNull(levels)
            android.util.Log.w("FreedomPlayer", "Este emulador no tiene ecualizador del sistema")
        }
    }

    @Test
    fun loudnessIsMeasuredWithTheSystemDecoders() {
        val lufs = AndroidLoudness.measureNow(wav(dir, "l.wav", 3.0).absolutePath)
        assertNotNull(lufs)
        assertTrue("un pitido de 440 Hz a −15 dBFS: $lufs LUFS", lufs!! in -16.5f..-14f)
        assertNull(runCatching { AndroidLoudness.measureNow(File(dir, "no-existe.webm").absolutePath) }.getOrNull())
    }
}
