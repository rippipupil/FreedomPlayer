package com.freedomplayer.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TempoTest {
    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 0.001f, "$expected ≈ $actual")

    @Test
    fun labels() {
        assertEquals("Normal", Tempo().label())
        assertEquals("1,25×", Tempo(125).label())
        assertEquals("0,85× · −2", Tempo(85, -2).label())
        assertEquals("+3", Tempo(100, 3).label())
        assertEquals("1×", Tempo.speedText(100))
        assertEquals("1,5×", Tempo.speedText(150))
        assertEquals("0,75×", Tempo.speedText(75))
    }

    @Test
    fun pitchAloneOrWithTheSpeed() {
        near(1f, Tempo(125).pitch)
        near(1.25f, Tempo(125, pitchWithSpeed = true).pitch)
        near(2f, Tempo(100, 12).pitch)
        near(0.85f * 1.12246f, Tempo(85, 2, pitchWithSpeed = true).pitch)
        assertTrue(Tempo().isNormal)
        assertFalse(Tempo(100, 1).isNormal)
    }

    @Test
    fun theStylesAreVinylLike() {
        val (name, nightcore) = Tempo.PRESETS.last()
        assertEquals("Nightcore", name)
        near(nightcore.speed, nightcore.pitch)
        assertTrue(Tempo.PRESETS.first().second.isNormal)
    }
}
