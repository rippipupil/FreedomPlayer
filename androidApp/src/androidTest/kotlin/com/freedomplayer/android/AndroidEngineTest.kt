package com.freedomplayer.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freedomplayer.android.AudioFiles.onMain
import com.freedomplayer.android.AudioFiles.track
import com.freedomplayer.android.AudioFiles.wav
import com.freedomplayer.android.playback.AndroidEngine
import com.freedomplayer.android.playback.Players
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.StreamSource
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
 * falta, el orden aleatorio sin cortes y la vista previa.
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
            engine.load(emptyList(), 0, 0, play = false)
            engine.release()
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
}
