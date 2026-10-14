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

    @Test
    fun movingSongsKeepsTheOneThatPlays() {
        val q = PlayQueue.of(ids, "b")
        // Una de después, antes de la que suena: la que suena baja un puesto.
        val up = q.move(3, 0)
        assertEquals(listOf("d", "a", "b", "c"), up.items)
        assertEquals("b", up.current)
        // Una de antes, después de la que suena: sube un puesto.
        val down = q.move(0, 3)
        assertEquals(listOf("b", "c", "d", "a"), down.items)
        assertEquals("b", down.current)
        // La que suena también se puede mover, y sigue sonando.
        val moved = q.move(1, 3)
        assertEquals(listOf("a", "c", "d", "b"), moved.items)
        assertEquals(3, moved.index)
        assertEquals(listOf("a", "c", "d", "b"), moved.original, "sin aleatorio, el orden nuevo es el de la lista")
        // Movimientos que no tienen sentido no cambian nada.
        assertEquals(q, q.move(1, 1))
        assertEquals(q, q.move(-1, 2))
        assertEquals(q, q.move(0, 9))
    }

    @Test
    fun movingWithShuffleKeepsTheOriginalOrder() {
        val q = PlayQueue.of(ids, "a", shuffle = true, random = Random(3))
        val moved = q.move(1, 3)
        assertEquals(q.items.toSet(), moved.items.toSet())
        assertEquals("a", moved.current)
        assertEquals(ids, moved.original)
        assertEquals(ids, moved.withShuffle(false).items, "al quitar el aleatorio vuelve el orden de la lista")
    }

    @Test
    fun removingFromTheQueue() {
        val q = PlayQueue.of(ids, "c")
        val r = q.remove(0)
        assertEquals(listOf("b", "c", "d"), r.items)
        assertEquals("c", r.current)
        assertEquals(q, q.remove(2), "la que suena no se quita así")
        assertEquals(listOf("a", "b", "c"), q.remove(3).items)
    }
}
