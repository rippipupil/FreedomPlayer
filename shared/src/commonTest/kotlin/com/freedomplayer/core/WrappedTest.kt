package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WrappedTest {
    private val day = Stats.DAY_MS
    private val hour = 60L * 60 * 1000

    /** 1 de octubre de 2026 a las 0:00 (UTC). */
    private val oct1 = 20_727 * day

    private val songs = listOf(
        Song("a", "Luz", "Lumen", 120, 0, score = 9),
        Song("b", "Neón", "Lumen", 240, 0, score = 6),
        Song("c", "Calle", "Otra", 60, 0),
    )

    private val log = listOf(
        // Septiembre: «a» suena por primera vez.
        PlayEvent("a", oct1 - 2 * day + 22 * hour),
        // Octubre: de noche casi todo, tres días seguidos.
        PlayEvent("a", oct1 + 22 * hour),
        PlayEvent("a", oct1 + day + 22 * hour),
        PlayEvent("b", oct1 + 2 * day + 21 * hour),
        PlayEvent("c", oct1 + 10 * day + 9 * hour),
        // Una que ya no está en la biblioteca: no cuenta.
        PlayEvent("borrada", oct1 + 3 * day),
    )

    @Test
    fun periodsAreYearsWithTheirMonths() {
        assertEquals(YearMonth(2026, 10), YearMonth.of(oct1))
        assertEquals(
            listOf(WrappedPeriod(2026), WrappedPeriod(2026, 10), WrappedPeriod(2026, 9)),
            Wrapped.periods(log),
        )
        assertEquals("OCTUBRE 2026", WrappedPeriod(2026, 10).label)
        assertEquals("OCT 26", WrappedPeriod(2026, 10).short)
        assertEquals("Tu octubre", WrappedPeriod(2026, 10).title)
        assertEquals("Tu 2026", WrappedPeriod(2026).title)
    }

    @Test
    fun aMonth() {
        val r = Wrapped.report(songs, log, WrappedPeriod(2026, 10))!!
        assertEquals(4, r.plays)
        assertEquals((2 * 120 + 240 + 60) / 60, r.minutes)
        assertEquals(3, r.songs)
        assertEquals(2, r.artists)
        assertEquals(listOf("a" to 2, "c" to 1, "b" to 1), r.topSongs.map { it.first.id to it.second }, "empates, por título")
        assertEquals(listOf("Lumen" to 3, "Otra" to 1), r.topArtists)
        assertEquals(2, r.newSongs, "«b» y «c»; «a» ya sonó en septiembre")
        assertEquals(ListeningTime.Noche, r.time)
        assertEquals(2, r.byHour[22])
        assertEquals("a", r.bestRated?.id)
        assertEquals(3, r.streakDays)
    }

    @Test
    fun aYearAndAnEmptyOne() {
        val r = Wrapped.report(songs, log, WrappedPeriod(2026))!!
        assertEquals(5, r.plays)
        assertEquals(3, r.newSongs)
        assertNull(Wrapped.report(songs, log, WrappedPeriod(2025)))
        assertNull(Wrapped.report(songs, log, WrappedPeriod(2026, 11)))
    }

    @Test
    fun listeningTimes() {
        assertEquals(ListeningTime.Madrugada, ListeningTime.of(3))
        assertEquals(ListeningTime.Manana, ListeningTime.of(6))
        assertEquals(ListeningTime.Tarde, ListeningTime.of(19))
        assertEquals(ListeningTime.Noche, ListeningTime.of(20))
    }
}
