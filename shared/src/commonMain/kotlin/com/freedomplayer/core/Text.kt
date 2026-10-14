package com.freedomplayer.core

/** 201 → «3:21». */
fun formatTime(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    val sec = (s % 60).toString().padStart(2, '0')
    return "${s / 60}:$sec"
}

/** Saludo de la pantalla de inicio según la hora (0–23). */
fun greetingFor(hour: Int): String = when (hour) {
    in 6..13 -> "BUENOS DÍAS"
    in 14..20 -> "BUENAS TARDES"
    else -> "BUENAS NOCHES"
}

/** «1 CANCIÓN», «7 CANCIONES». */
fun songCount(n: Int): String = if (n == 1) "1 CANCIÓN" else "$n CANCIONES"

/**
 * Texto del aviso de descarga: una canción, «¡Añadida! Descargando y buscando letra…»;
 * un álbum o una playlist, «Descargando 3 de 12».
 */
fun downloadMessage(done: Int, total: Int): String =
    if (total <= 1) "¡Añadida! Descargando y buscando letra…" else "Descargando ${minOf(done + 1, total)} de $total"

/** «1 minuto», «15 minutos». */
fun minutesText(n: Int): String = if (n == 1) "1 minuto" else "$n minutos"

/** Lo que le queda al temporizador de apagado: «14:59», redondeando hacia arriba. */
fun timerLeftText(ms: Long): String = formatTime(((ms.coerceAtLeast(0) + 999) / 1000).toInt())

/** «3,5 ✦» para una nota de 7. */
fun starsText(score: Int): String = "${Ranking.label(score)} ✦"

/** «84×». */
fun timesText(n: Int): String = "$n×"

/** «88 k», «1,2 M»: visualizaciones y cifras grandes, en corto. */
fun compactCount(n: Long): String {
    fun one(value: Long, unit: Long, suffix: String): String {
        val tenths = (value * 10 + unit / 2) / unit
        val whole = tenths / 10
        val dec = tenths % 10
        return if (whole >= 100 || dec == 0L) "$whole $suffix" else "$whole,$dec $suffix"
    }
    return when {
        n < 0 -> ""
        n < 1_000 -> n.toString()
        n < 999_500 -> one(n, 1_000, "k")
        n < 999_500_000 -> one(n, 1_000_000, "M")
        else -> one(n, 1_000_000_000, "mil M")
    }
}

/** Colores de carátula mientras no hay imagen (los de la paleta del diseño). */
object CoverPalette {
    val colors: List<Long> = listOf(
        0xFF7A1F55, 0xFF3B2F8F, 0xFF1E5C50, 0xFF4A2A6B, 0xFF5B2340, 0xFF2A5E3F, 0xFF2B3F5E, 0xFF552243,
    )

    /** Siempre el mismo color para el mismo id. */
    fun forKey(key: String): Long {
        var h = 0
        for (c in key) h = h * 31 + c.code
        return colors[(h % colors.size + colors.size) % colors.size]
    }
}
