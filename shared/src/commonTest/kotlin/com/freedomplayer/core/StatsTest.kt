package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Estadísticas tipo Wrapped: meses del calendario, escuchas por mes y lo más destacado del ranking. */
class StatsTest {
    private val hour = 3_600_000L

    // 14 de octubre de 2026 a las 00:00 UTC, y otras fechas conocidas.
    private val oct14 = 1_791_936_000_000L
    private val oct31Late = 1_793_489_400_000L // 31-10-2026 23:30 UTC
    private val leapDay = 1_709_208_000_000L // 29-02-2024 12:00 UTC

    @Test
    fun monthsOfTheCalendar() {
        assertEquals(YearMonth(1970, 1), YearMonth.of(0))
        assertEquals(YearMonth(1969, 12), YearMonth.of(-hour))
        assertEquals(YearMonth(2026, 10), YearMonth.of(oct14))
        assertEquals(YearMonth(2024, 2), YearMonth.of(leapDay))
        assertEquals(YearMonth(2000, 3), YearMonth.of(951_868_800_000L))
        // Con la hora local: las 23:30 UTC del 31 de octubre ya son noviembre en Madrid (+1 h).
        assertEquals(YearMonth(2026, 10), YearMonth.of(oct31Late))
        assertEquals(YearMonth(2026, 11), YearMonth.of(oct31Late, offsetMillis = hour))
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12).plus(1))
        assertEquals(YearMonth(2025, 11), YearMonth(2026, 10).plus(-11))
        assertTrue(YearMonth(2026, 1) > YearMonth(2025, 12))
        assertEquals("OCTUBRE 2026", YearMonth(2026, 10).label)
        assertEquals("SEP", YearMonth(2026, 9).short)
    }

    private val songs = listOf(
        Song("a", "Alfa", "Uno", durationSec = 120, coverColor = 0, score = 10),
        Song("b", "Beta", "Uno", durationSec = 180, coverColor = 0, score = 8),
        Song("c", "Gamma", "Dos", durationSec = 240, coverColor = 0),
        Song("d", "Delta", "Dos", durationSec = 60, coverColor = 0, score = 4),
    )

    private val day = 24 * hour

    /** Septiembre: a ×2; octubre: b ×3, c ×4 (c suena por primera vez) y a ×1. */
    private val log = listOf(
        PlayEvent("a", oct14 - 30 * day),
        PlayEvent("a", oct14 - 29 * day),
        PlayEvent("b", oct14 - 2 * day),
        PlayEvent("b", oct14 - day),
        PlayEvent("b", oct14),
        PlayEvent("c", oct14 + hour),
        PlayEvent("c", oct14 + 2 * hour),
        PlayEvent("c", oct14 + 3 * hour),
        PlayEvent("c", oct14 + 4 * hour),
        PlayEvent("a", oct14 + 5 * hour),
    )

    @Test
    fun aMonthOfListening() {
        val oct = YearMonth(2026, 10)
        assertEquals(listOf(oct, YearMonth(2026, 9)), Stats.months(log))
        val counts = Stats.playsIn(log, oct)
        assertEquals(mapOf("b" to 3, "c" to 4, "a" to 1), counts)
        val s = Stats.summary(songs, counts)
        assertEquals(8, s.totalPlays)
        assertEquals((3 * 180 + 4 * 240 + 120) / 60, s.minutes)
        assertEquals(listOf("c", "b", "a"), s.topSongs.map { it.id })
        assertEquals(listOf("Dos" to 4, "Uno" to 4), s.topArtists, "empate: por nombre")
        assertEquals(setOf("b", "c"), Stats.discoveredIn(log, oct))

        val perMonth = Stats.playsPerMonth(log, oct, count = 3)
        assertEquals(listOf(YearMonth(2026, 8) to 0, YearMonth(2026, 9) to 2, oct to 8), perMonth)
    }

    @Test
    fun rankingHighlights() {
        val oct = YearMonth(2026, 10)
        val counts = Stats.playsIn(log, oct)
        val h = Stats.highlights(songs, counts, Stats.discoveredIn(log, oct)).associateBy { it.label }
        assertEquals("Alfa", h.getValue("TU N.º 1 DEL RANKING").title)
        assertTrue(h.getValue("TU N.º 1 DEL RANKING").detail.contains("sonó 1 vez"))
        assertEquals("Beta", h.getValue("LA QUE MÁS SUENA CON NOTA").title)
        assertEquals("Gamma", h.getValue("SIN NOTA Y SIN PARAR").title)
        assertEquals("Uno", h.getValue("TU ARTISTA MEJOR PUNTUADO").title, "media de 9 (dos canciones)")
        assertEquals("Gamma", h.getValue("DESCUBRIMIENTO").title)

        // Sin escuchas ni notas no se inventa nada.
        assertTrue(Stats.highlights(songs.map { it.copy(score = null) }, emptyMap()).isEmpty())
    }
}
