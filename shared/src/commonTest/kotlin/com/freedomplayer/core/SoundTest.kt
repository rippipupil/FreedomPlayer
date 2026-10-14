package com.freedomplayer.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El ecualizador, la medida de sonoridad (LUFS), el volumen igualado y las curvas del fundido. */
class SoundTest {
    private val rate = 44_100

    /** Un seno estéreo de [freq] Hz y amplitud [amp] (de 0 a 1). */
    private fun sine(freq: Double, amp: Double, seconds: Double): FloatArray {
        val frames = (seconds * rate).toInt()
        return FloatArray(frames * 2) { i -> (amp * sin(2 * PI * freq * (i / 2) / rate)).toFloat() }
    }

    /** Nivel RMS en dB, sin el primer medio segundo (lo que tarda el filtro en asentarse). */
    private fun rmsDb(samples: FloatArray): Double {
        val skip = rate
        var sum = 0.0
        for (i in skip until samples.size) sum += samples[i].toDouble() * samples[i]
        return 20 * log10(sqrt(sum / (samples.size - skip)))
    }

    private fun gainAt(freq: Double, gains: List<Float>): Double {
        val signal = sine(freq, 0.1, 2.0)
        val before = rmsDb(signal)
        Equalizer(rate, 2).apply { setGains(gains) }.process(signal, signal.size / 2)
        return rmsDb(signal) - before
    }

    @Test
    fun flatLeavesTheSoundAlone() {
        val signal = sine(440.0, 0.3, 1.0)
        val copy = signal.copyOf()
        Equalizer(rate, 2).apply { setGains(EqPreset.Flat.gains) }.process(signal, signal.size / 2)
        assertTrue(signal.contentEquals(copy))
    }

    @Test
    fun eachBandMovesItsOwnFrequencies() {
        val middle = listOf(0f, 0f, 6f, 0f, 0f)
        assertEquals(6.0, gainAt(910.0, middle), 0.5)
        assertTrue(abs(gainAt(60.0, middle)) < 1.0, "60 Hz casi no cambia")
        assertTrue(abs(gainAt(14_000.0, middle)) < 1.0, "14 kHz casi no cambia")

        val bass = listOf(6f, 0f, 0f, 0f, 0f)
        assertTrue(gainAt(30.0, bass) in 4.0..6.5, "el estante de graves sube lo de abajo")
        assertTrue(abs(gainAt(3_600.0, bass)) < 0.5)

        val treble = listOf(0f, 0f, 0f, 0f, -6f)
        assertTrue(gainAt(18_000.0, treble) in -6.5..-4.0, "el estante de agudos baja lo de arriba")
        assertTrue(abs(gainAt(230.0, treble)) < 0.5)
    }

    @Test
    fun otherBandLayoutsGetTheNearestGains() {
        val g = listOf(6f, 0f, -3f, 0f, 4f)
        assertEquals(6f, EqBands.gainAt(g, 60f))
        assertEquals(6f, EqBands.gainAt(g, 31f), "por debajo, la primera")
        assertEquals(-3f, EqBands.gainAt(g, 910f))
        assertEquals(4f, EqBands.gainAt(g, 16_000f), "por encima, la última")
        val between = EqBands.gainAt(g, 117f)
        assertTrue(between in 0f..6f && abs(between - 3f) < 0.3f, "a medio camino (en octavas) entre 60 y 230: $between")
        assertEquals(listOf(6f, 0f, -3f, 0f, 4f), EqBands.parse(EqBands.format(g)))
        assertEquals(EqBands.flat, EqBands.parse(null))
        assertEquals(EqBands.flat, EqBands.parse("basura"))
        assertEquals(-3f, EqBands.preampDb(g), "lo que más sube, a la mitad")
        assertEquals(0f, EqBands.preampDb(listOf(-2f, -2f, 0f, 0f, 0f)))
    }

    @Test
    fun gainsAreLimitedAndPresetsAreRecognised() {
        assertEquals(listOf(12f, -12f, 0f, 0f, 0f), EqBands.clamp(listOf(20f, -30f)))
        assertEquals(EqPreset.Rock, EqPreset.matching(EqPreset.Rock.gains))
        assertEquals(EqPreset.Custom, EqPreset.matching(listOf(1f, 2f, 3f, 4f, 5f)))
        assertTrue(EqPreset.Custom !in EqPreset.choices)
    }

    @Test
    fun loudnessOfAKnownTone() {
        // Un seno de 1 kHz a −20 dBFS en los dos canales mide −20 LUFS.
        val meter = LoudnessMeter(rate, 2)
        val tone = sine(1_000.0, 0.1, 5.0)
        meter.add(tone, tone.size / 2)
        assertEquals(-20.0, meter.integrated()!!.toDouble(), 0.3)

        // A trozos da lo mismo, y el silencio del final no cuenta (umbral absoluto).
        val parts = LoudnessMeter(rate, 2)
        val chunk = 4_096
        var at = 0
        while (at < tone.size / 2) {
            val n = minOf(chunk, tone.size / 2 - at)
            parts.add(tone.copyOfRange(at * 2, (at + n) * 2), n)
            at += n
        }
        val silence = FloatArray(rate * 2 * 5)
        parts.add(silence, silence.size / 2)
        assertEquals(-20.0, parts.integrated()!!.toDouble(), 0.3)
    }

    @Test
    fun silenceOrTooShortHasNoLoudness() {
        val meter = LoudnessMeter(rate, 2)
        val silence = FloatArray(rate * 2 * 3)
        meter.add(silence, silence.size / 2)
        assertNull(meter.integrated())
        val short = LoudnessMeter(rate, 2)
        val bit = sine(1_000.0, 0.5, 0.2)
        short.add(bit, bit.size / 2)
        assertNull(short.integrated())
    }

    @Test
    fun louderSongsAreTurnedDown() {
        assertEquals(-6f, Loudness.gainDb(-10f))
        assertEquals(0f, Loudness.gainDb(-20f), "sin margen para subir, las flojas se quedan igual")
        assertEquals(4f, Loudness.gainDb(-20f, maxBoostDb = 6f))
        assertEquals(6f, Loudness.gainDb(-40f, maxBoostDb = 6f))
        assertEquals(0f, Loudness.gainDb(null))
        assertEquals(0.5f, Loudness.linear(-6.0206f), 0.001f)
    }

    @Test
    fun crossfadeKeepsTheSamePower() {
        assertEquals(0f, Crossfade.fadeIn(0f), 1e-6f)
        assertEquals(1f, Crossfade.fadeIn(1f), 1e-6f)
        assertEquals(1f, Crossfade.fadeOut(0f), 1e-6f)
        assertEquals(0f, Crossfade.fadeOut(1f), 1e-6f)
        for (p in listOf(0.1f, 0.25f, 0.5f, 0.9f)) {
            val i = Crossfade.fadeIn(p)
            val o = Crossfade.fadeOut(p)
            assertEquals(1f, i * i + o * o, 1e-5f)
        }
        assertEquals(1f, Crossfade.fadeIn(3f), "fuera de rango se queda en los extremos")
    }
}
