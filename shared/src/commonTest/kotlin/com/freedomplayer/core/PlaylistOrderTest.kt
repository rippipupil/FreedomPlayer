package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistOrderTest {
    private fun song(id: String, score: Int? = null, addedAt: Long = 0) = Song(id, id, "Artista", 180, 0, score = score, addedAt = addedAt)

    // a entró en la playlist la primera y d la última; c no tiene fecha (llegó al sincronizar) y
    // se usa cuándo entró en la biblioteca.
    private val songs = listOf(song("a", 6), song("b", 9), song("c", addedAt = 250), song("d", 6))
    private val playlist = Playlist("p", "Noches", listOf("a", "b", "c", "d"), 0, addedAt = mapOf("a" to 100L, "b" to 200L, "d" to 400L))

    private fun ids(order: PlaylistOrder) = order.sort(playlist, songs).map { it.id }

    @Test
    fun newestFirstByDefault() {
        assertEquals(listOf("d", "c", "b", "a"), ids(PlaylistOrder()))
        assertEquals(listOf("a", "b", "c", "d"), ids(PlaylistOrder(PlaylistSort.Added, descending = false)))
    }

    @Test
    fun byScoreUnratedGoLastAndTiesNewestFirst() {
        assertEquals(listOf("b", "d", "a", "c"), ids(PlaylistOrder(PlaylistSort.Score)))
        assertEquals(listOf("d", "a", "b", "c"), ids(PlaylistOrder(PlaylistSort.Score, descending = false)))
    }

    @Test
    fun withoutDatesThePlaylistOrderDecides() {
        val old = Playlist("p", "Vieja", listOf("x", "y", "z"), 0)
        val list = listOf(song("x"), song("y"), song("z"))
        assertEquals(listOf("z", "y", "x"), PlaylistOrder().sort(old, list).map { it.id })
        assertEquals(listOf("x", "y", "z"), PlaylistOrder(descending = false).sort(old, list).map { it.id })
    }

    @Test
    fun tappingTheChosenOneFlipsIt() {
        val byDate = PlaylistOrder()
        assertEquals(PlaylistOrder(PlaylistSort.Added, descending = false), byDate.tap(PlaylistSort.Added))
        assertEquals(PlaylistOrder(PlaylistSort.Score, descending = true), byDate.flipped().tap(PlaylistSort.Score))
    }

    @Test
    fun savedAndReadBack() {
        PlaylistSort.entries.forEach { by ->
            listOf(true, false).forEach { desc ->
                val o = PlaylistOrder(by, desc)
                assertEquals(o, PlaylistOrder.decode(o.encode()))
            }
        }
        assertEquals(PlaylistOrder(), PlaylistOrder.decode(null))
        assertEquals(PlaylistOrder(), PlaylistOrder.decode("rara"))
    }
}
