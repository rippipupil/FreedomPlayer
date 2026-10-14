package com.freedomplayer.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayQueueTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun startsAtTheChosenSong() {
        val q = PlayQueue.of(ids, "b")
        assertEquals("b", q.current)
        assertEquals(listOf("c", "d"), q.upcoming)
    }

    @Test
    fun nextStopsAtTheEndWithoutRepeat() {
        val q = PlayQueue.of(ids, "c")
        assertEquals("d", q.next()?.current)
        assertNull(q.next()!!.next())
    }

    @Test
    fun repeatAllWrapsAround() {
        val q = PlayQueue.of(ids, "d", repeat = RepeatMode.All)
        assertEquals("a", q.next()?.current)
        assertEquals("d", PlayQueue.of(ids, "a", repeat = RepeatMode.All).previous().current)
    }

    @Test
    fun repeatOneRepeatsOnlyWhenTheSongEndsByItself() {
        val q = PlayQueue.of(ids, "b", repeat = RepeatMode.One)
        assertEquals("b", q.next(auto = true)?.current)
        assertEquals("c", q.next()?.current)
    }

    @Test
    fun previousStaysOnTheFirstSongWithoutRepeat() {
        assertEquals("a", PlayQueue.of(ids, "a").previous().current)
        assertEquals("b", PlayQueue.of(ids, "c").previous().current)
    }

    @Test
    fun repeatButtonCyclesOffAllOne() {
        val q = PlayQueue.of(ids)
        assertEquals(RepeatMode.All, q.cycleRepeat().repeat)
        assertEquals(RepeatMode.One, q.cycleRepeat().cycleRepeat().repeat)
        assertEquals(RepeatMode.Off, q.cycleRepeat().cycleRepeat().cycleRepeat().repeat)
    }

    @Test
    fun shuffleKeepsTheCurrentSongFirstAndUnshuffleRestoresTheOrder() {
        val q = PlayQueue.of(ids, "c").withShuffle(true, Random(7))
        assertEquals("c", q.current)
        assertEquals(0, q.index)
        assertEquals(ids.toSet(), q.items.toSet())
        assertEquals(ids.size, q.items.size)

        val back = q.next()!!.withShuffle(false)
        assertEquals(ids, back.items)
        assertEquals(back.items.indexOf(back.current), back.index)
        assertTrue(back.current in ids)
    }

    @Test
    fun playNextAddAndRemove() {
        val q = PlayQueue.of(ids, "b").playNext("x")
        assertEquals(listOf("x", "c", "d"), q.upcoming)

        val added = q.add("y")
        assertEquals("y", added.items.last())

        val removed = added.remove(0)
        assertEquals(listOf("b", "x", "c", "d", "y"), removed.items)
        assertEquals("b", removed.current)
        assertEquals(removed, removed.remove(removed.index), "la que suena no se quita")
    }

    @Test
    fun songsAddedWhileShuffledSurviveUnshuffle() {
        val q = PlayQueue.of(ids, "a").withShuffle(true, Random(3)).playNext("x")
        val back = q.withShuffle(false)
        assertEquals(listOf("a", "x", "b", "c", "d"), back.items)
        assertEquals("a", back.current)
    }

    @Test
    fun emptyQueueIsHarmless() {
        val q = PlayQueue.of(emptyList())
        assertNull(q.current)
        assertNull(q.next())
        assertEquals(q, q.previous())
        assertEquals("z", q.add("z").current)
    }
}
