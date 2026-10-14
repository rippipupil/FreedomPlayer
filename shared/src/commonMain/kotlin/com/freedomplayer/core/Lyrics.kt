package com.freedomplayer.core

/**
 * Una línea de letra: milisegundo en que empieza y texto. En una letra sin sincronizar todas las
 * líneas llevan [UNSYNCED].
 */
data class LyricLine(val atMs: Long, val text: String) {
    companion object {
        const val UNSYNCED = -1L
    }
}

/** La letra de una canción tal y como se guarda (sale de LRCLIB o de un archivo propio). */
data class LyricsData(
    /** Letra sincronizada en formato LRC («[01:12.30] texto»), o null. */
    val synced: String?,
    /** Letra sin tiempos, o null. */
    val plain: String?,
    /** Es instrumental: no tiene letra. */
    val instrumental: Boolean = false,
) {
    /** Las líneas que se enseñan: las sincronizadas si las hay; si no, la letra sin tiempos. */
    fun lines(): List<LyricLine> {
        val timed = synced?.let { Lrc.parse(it) }.orEmpty()
        if (timed.isNotEmpty()) return timed
        return plain?.let { Lrc.plain(it) }.orEmpty()
    }

    val isEmpty: Boolean get() = synced.isNullOrBlank() && plain.isNullOrBlank()
}

/** Lee letras en formato LRC, el de LRCLIB. */
object Lrc {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTag = Regex("""^\[([a-zA-Z#]+):(.*)]\s*$""")
    private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /** Lo que se enseña en las líneas vacías de una letra sincronizada (pausas instrumentales). */
    const val BREAK = "♪"

    /**
     * Las líneas con tiempo, ordenadas. Admite varias marcas en una línea («[00:12.00][00:45.00] estribillo»),
     * centésimas o milésimas, «[offset:+500]» (positivo = la letra sale antes) y quita las marcas por
     * palabra del LRC ampliado («<00:12.50>»). Las líneas sin tiempo se ignoran.
     */
    fun parse(text: String): List<LyricLine> {
        var offset = 0L
        val out = ArrayList<LyricLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            val meta = metaTag.matchEntire(line)
            if (meta != null && !timeTag.containsMatchIn(line)) {
                if (meta.groupValues[1].equals("offset", ignoreCase = true)) {
                    offset = meta.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
                }
                continue
            }
            // Las marcas de tiempo van todas al principio.
            var rest = line
            val times = ArrayList<Long>()
            while (true) {
                val m = timeTag.find(rest) ?: break
                if (m.range.first != 0) break
                times += toMillis(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            if (times.isEmpty()) continue
            val words = rest.replace(wordTag, "").replace(Regex("""\s{2,}"""), " ").trim()
            for (t in times) out += LyricLine((t - offset).coerceAtLeast(0L), words.ifEmpty { BREAK })
        }
        // Una pausa al final (o varias seguidas) no aporta nada.
        val sorted = out.sortedBy { it.atMs }
        return sorted.filterIndexed { i, l -> !(l.text == BREAK && (i == sorted.lastIndex || sorted[i + 1].text == BREAK)) }
    }

    /** Una letra sin tiempos, línea a línea (sin las vacías del principio y del final). */
    fun plain(text: String): List<LyricLine> =
        text.lines().map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
            .map { LyricLine(LyricLine.UNSYNCED, it) }

    /** ¿Tiene alguna marca de tiempo? */
    fun isSynced(text: String?): Boolean = text != null && text.lineSequence().any { timeTag.find(it.trim())?.range?.first == 0 }

    private fun toMillis(min: String, sec: String, frac: String): Long {
        val fraction = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fraction
    }
}

object Lyrics {
    /** Índice de la línea que toca en [positionMs]; -1 si todavía no ha empezado ninguna o no está sincronizada. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int =
        if (lines.isEmpty() || lines[0].atMs == LyricLine.UNSYNCED) -1 else lines.indexOfLast { it.atMs <= positionMs }

    /**
     * Las [size] líneas que se ven alrededor de la actual (una antes y el resto después),
     * como en el reproductor del diseño. Devuelve el índice de la primera.
     */
    fun windowStart(lines: List<LyricLine>, current: Int, size: Int = 4): Int =
        maxOf(0, minOf(current - 1, lines.size - size))
}
