package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhostCollectionTest {
    private val day = Stats.DAY_MS
    private val hour = 60L * 60 * 1000

    private fun song(id: String, score: Int? = null) = Song(id, id, "Artista", 180, 0, score = score)

    @Test
    fun theClassicIsAlwaysYours() {
        assertEquals(setOf(GhostSkin.Clasico), GhostCollection.unlocked(GhostFacts()))
    }

    @Test
    fun runsOfDays() {
        assertEquals(0, GhostCollection.longestRun(emptySet()))
        assertEquals(3, GhostCollection.longestRun(setOf(1L, 2L, 3L, 7L, 9L, 10L)))
        assertEquals(1, GhostCollection.longestRun(setOf(5L)))
    }

    @Test
    fun factsFromTheLibraryAndTheHistory() {
        val songs = listOf(song("a", 10), song("b", 6), song("c"))
        val base = 20_000 * day
        // 7 días seguidos a las 23 h (hora local) y 2 escuchas de madrugada (a las 3 h).
        val log = (0 until 7).map { PlayEvent("a", base + it * day + 23 * hour) } +
            listOf(PlayEvent("b", base + 3 * hour), PlayEvent("c", base + day + 3 * hour))
        val f = GhostCollection.facts(songs, log, wrappedSeen = true)
        assertEquals(9, f.plays)
        assertEquals(2, f.rated)
        assertEquals(1, f.perfectTens)
        assertEquals(3, f.librarySize)
        assertEquals(2, f.nightPlays)
        assertEquals(7, f.bestStreakDays)
        val won = GhostCollection.unlocked(f)
        assertTrue(GhostSkin.Rey in won)
        assertTrue(GhostSkin.Constante in won)
        assertTrue(GhostSkin.Fiesta in won)
        assertFalse(GhostSkin.Menta in won)
        assertFalse(GhostSkin.Buho in won)
    }

    @Test
    fun theHourIsTheLocalOne() {
        // A las 23 h en UTC es la 1 de la madrugada a UTC+2: cuenta como de noche.
        val log = listOf(PlayEvent("a", 20_000 * day + 23 * hour))
        assertEquals(0, GhostCollection.facts(emptyList(), log, false).nightPlays)
        assertEquals(1, GhostCollection.facts(emptyList(), log, false) { 2 * hour }.nightPlays)
    }

    @Test
    fun progressTowardsEachGoal() {
        val f = GhostFacts(plays = 120, rated = 3)
        assertEquals(120, GhostCollection.progress(GhostSkin.Dj, f))
        assertTrue(GhostCollection.isUnlocked(GhostSkin.Menta, f))
        assertFalse(GhostCollection.isUnlocked(GhostSkin.Dj, f))
        assertFalse(GhostCollection.isUnlocked(GhostSkin.Lavanda, f))
    }
}
