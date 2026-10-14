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

/** «3,5 ✦» para una nota de 7. */
fun starsText(score: Int): String = "${Ranking.label(score)} ✦"

/** «84×». */
fun timesText(n: Int): String = "$n×"
