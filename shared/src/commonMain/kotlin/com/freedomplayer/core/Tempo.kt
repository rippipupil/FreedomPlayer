package com.freedomplayer.core

import kotlin.math.pow

/**
 * Velocidad y tono de lo que suena. [speedPercent] 100 es tal cual; [semitones] sube o baja el
 * tono; con [pitchWithSpeed] el tono va con la velocidad, como un vinilo (lo de «slowed» y
 * «nightcore»).
 */
data class Tempo(val speedPercent: Int = 100, val semitones: Int = 0, val pitchWithSpeed: Boolean = false) {
    val speed: Float get() = speedPercent / 100f

    /** Lo que se le pide al reproductor: 1 es el tono de siempre. */
    val pitch: Float get() = (if (pitchWithSpeed) speed else 1f) * 2f.pow(semitones / 12f)

    val isNormal: Boolean get() = speedPercent == 100 && semitones == 0

    /** «1,25×», «0,85× · −2», «Normal»… para enseñarlo al lado del botón. */
    fun label(): String {
        if (isNormal) return "Normal"
        val parts = mutableListOf<String>()
        if (speedPercent != 100) parts += speedText(speedPercent)
        if (semitones != 0) parts += semitoneText(semitones)
        return parts.joinToString(" · ")
    }

    companion object {
        /** Lo que se puede elegir de velocidad (en %). */
        val SPEEDS = listOf(75, 85, 100, 115, 125, 150)

        /** Lo que se puede subir o bajar el tono (semitonos). */
        val SEMITONES = -3..3

        val PRESETS = listOf(
            "Normal" to Tempo(),
            "Slowed" to Tempo(85, 0, pitchWithSpeed = true),
            "Sped up" to Tempo(115, 0, pitchWithSpeed = true),
            "Nightcore" to Tempo(125, 0, pitchWithSpeed = true),
        )

        /** 125 → «1,25×», 100 → «1×», 85 → «0,85×». */
        fun speedText(percent: Int): String {
            val whole = percent / 100
            val frac = (percent % 100).toString().padStart(2, '0').trimEnd('0')
            return if (frac.isEmpty()) "$whole×" else "$whole,$frac×"
        }

        /** 2 → «+2», −1 → «−1», 0 → «0». */
        fun semitoneText(semitones: Int): String = when {
            semitones > 0 -> "+$semitones"
            semitones < 0 -> "−${-semitones}"
            else -> "0"
        }
    }
}
