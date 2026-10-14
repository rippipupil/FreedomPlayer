package com.freedomplayer.data

import com.freedomplayer.core.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cómo se tiene que portar un reproductor (el simulado de las pruebas, Media3 en Android y el
 * del PC): sigue la cola, repetir todo/una, fin de la cola, reordenar sin cortar y vista previa.
 */
class EngineContractTest {
    private fun track(id: String, sec: Int = 10) = Track(id, "file:///$id", id, "Artista", null, null, sec * 1000L)

    private val tracks = listOf(track("a"), track("b"), track("c"))

    private fun engine(repeat: RepeatMode = RepeatMode.Off, start: Int = 0) = SimulatedEngine().apply {
        setRepeat(repeat)
        load(tracks, start, 0, play = true)
    }

    @Test
    fun followsTheQueueAndStopsAtTheEnd() {
        val e = engine()
        val first = e.snapshot().transitions
        e.tick(10_000)
        assertEquals(1, e.snapshot().index)
        assertEquals(first + 1, e.snapshot().transitions)
        e.tick(10_000)
        e.tick(10_000)
        val end = e.snapshot()
        assertEquals(2, end.index)
        assertTrue(end.ended)
        assertFalse(end.playing)
        e.play()
        assertEquals(0, e.snapshot().positionMs, "al dar a reproducir tras el final, vuelve a empezar la última")
        assertTrue(e.snapshot().playing)
    }

    @Test
    fun repeatAllWrapsAndRepeatOneStays() {
        val all = engine(RepeatMode.All, start = 2)
        all.tick(10_000)
        assertEquals(0, all.snapshot().index)
        assertTrue(all.snapshot().playing)

        val one = engine(RepeatMode.One, start = 1)
        val before = one.snapshot().transitions
        one.tick(10_000)
        assertEquals(1, one.snapshot().index)
        assertEquals(before + 1, one.snapshot().transitions, "cada vuelta cuenta como una escucha nueva")
        assertEquals(0, one.snapshot().positionMs)
    }

    @Test
    fun reorderKeepsTheSongPlaying() {
        val e = engine(start = 1)
        e.tick(4_000)
        e.reorder(listOf(track("b"), track("c"), track("a")), 0)
        val s = e.snapshot()
        assertEquals(0, s.index)
        assertEquals(4_000, s.positionMs)
        assertTrue(s.playing)
        e.tick(6_000)
        assertEquals(1, e.snapshot().index, "después suena la siguiente del orden nuevo")
    }

    @Test
    fun skipSeekAndPause() {
        val e = engine()
        e.skipTo(2)
        assertEquals(2, e.snapshot().index)
        e.seekTo(99_000)
        assertEquals(10_000, e.snapshot().positionMs, "no se pasa del final")
        e.pause()
        e.tick(5_000)
        assertFalse(e.snapshot().playing)
        e.skipTo(7)
        assertEquals(2, e.snapshot().index, "una posición que no existe no cambia nada")
    }

    @Test
    fun previewRunsAloneAndEnds() {
        val e = engine()
        e.tick(2_000)
        e.pause()
        e.startPreview(StreamSource("demo:x", durationSec = 3))
        e.tick(1_000)
        val s = e.snapshot()
        assertEquals(2_000, s.positionMs, "lo que sonaba se queda donde estaba")
        assertEquals(1_000, assertNotNull(s.preview).positionMs)
        e.previewPause()
        e.tick(1_000)
        assertEquals(1_000, e.snapshot().preview?.positionMs)
        e.previewPlay()
        e.tick(5_000)
        assertTrue(e.snapshot().preview?.ended == true)
        e.stopPreview()
        assertNull(e.snapshot().preview)
    }

    @Test
    fun anEmptyQueueIsHarmless() {
        val e = SimulatedEngine()
        e.load(emptyList(), 0, 0, play = true)
        e.play()
        e.tick(1_000)
        assertEquals(-1, e.snapshot().index)
        assertFalse(e.snapshot().playing)
    }
}
