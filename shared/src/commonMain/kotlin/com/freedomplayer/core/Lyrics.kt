package com.freedomplayer.core

/** Una línea de letra sincronizada (formato LRC de LRCLIB): segundo en que empieza y texto. */
data class LyricLine(val atSec: Int, val text: String)

object Lyrics {
    /** Índice de la línea que toca en [positionSec]; -1 si todavía no ha empezado ninguna. */
    fun currentIndex(lines: List<LyricLine>, positionSec: Int): Int = lines.indexOfLast { it.atSec <= positionSec }

    /**
     * Las [size] líneas que se ven alrededor de la actual (una antes y el resto después),
     * como en el reproductor del diseño. Devuelve el índice de la primera.
     */
    fun windowStart(lines: List<LyricLine>, current: Int, size: Int = 4): Int =
        maxOf(0, minOf(current - 1, lines.size - size))
}
