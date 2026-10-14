package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RankingTest {
    private fun song(id: String, score: Int?, plays: Int = 0, title: String = id) =
        Song(id, title, "x", 180, 0xFF000000, plays = plays, score = score)

    @Test
    fun generalOrdersByScoreThenPlays() {
        val songs = listOf(song("a", 8, 10), song("b", 10, 1), song("c", 8, 50), song("d", null, 999))
        assertEquals(listOf("b", "c", "a"), Ranking.general(songs).map { it.id })
    }

    @Test
    fun unratedListsSongsWithoutScoreByPlays() {
        val songs = listOf(song("a", null, 1), song("b", 5), song("c", null, 9))
        assertEquals(listOf("c", "a"), Ranking.unrated(songs).map { it.id })
    }

    @Test
    fun unratedSkipsSongsNeverPlayed() {
        val songs = listOf(song("nueva", null, 0), song("a", null, 3))
        assertEquals(listOf("a"), Ranking.unrated(songs).map { it.id })
    }

    @Test
    fun starsForHalfScores() {
        val f = StarFill.Full; val h = StarFill.Half; val e = StarFill.Empty
        assertEquals(listOf(f, f, f, f, h), Ranking.stars(9))
        assertEquals(listOf(f, f, f, f, f), Ranking.stars(10))
        assertEquals(listOf(h, e, e, e, e), Ranking.stars(1))
        assertEquals(List(5) { e }, Ranking.stars(null))
    }

    @Test
    fun tapLeftHalfGivesHalfStar() {
        assertEquals(1, Ranking.scoreFromTap(0, leftHalf = true))
        assertEquals(2, Ranking.scoreFromTap(0, leftHalf = false))
        assertEquals(9, Ranking.scoreFromTap(4, leftHalf = true))
        assertEquals(10, Ranking.scoreFromTap(4, leftHalf = false))
    }

    @Test
    fun labelShowsHalves() {
        assertEquals("4,5", Ranking.label(9))
        assertEquals("5", Ranking.label(10))
    }

    @Test
    fun moveReordersCustomRanking() {
        val r = CustomRanking("r", "Top", listOf("a", "b", "c", "d"))
        assertEquals(listOf("c", "a", "b", "d"), Ranking.move(r, 2, 0).songIds)
        assertEquals(r, Ranking.move(r, 0, 9))
    }

    @Test
    fun rateClampsAndClears() {
        val songs = listOf(song("a", null), song("b", 4))
        assertEquals(10, Ranking.rate(songs, "a", 15).first().score)
        assertEquals(null, Ranking.rate(songs, "b", null)[1].score)
    }

    @Test
    fun formatsTime() {
        assertEquals("3:21", formatTime(201))
        assertEquals("0:05", formatTime(5))
    }
}
