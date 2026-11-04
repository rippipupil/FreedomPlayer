package com.freedomplayer.ui

import com.freedomplayer.core.Playlist
import com.freedomplayer.core.PlaylistOrder
import com.freedomplayer.core.PlaylistSort
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.core.Song
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.LibrarySnapshot
import com.freedomplayer.data.MemoryStore
import com.freedomplayer.data.SampleLyrics
import com.freedomplayer.data.SampleSource
import com.freedomplayer.data.Services
import com.freedomplayer.data.SimulatedDownloader
import com.freedomplayer.data.SimulatedEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El orden de las playlists (fecha o nota) visto desde la app, puntuar la que suena y el botón de
 * repetir de la notificación.
 */
class PlaylistOrderFlowTest {
    private val ids = (1..4).map { "s$it" }

    private fun songs() = ids.mapIndexed { i, id ->
        Song(id, "Canción ${i + 1}", "Artista", durationSec = 60, coverColor = 0, audioPath = "/musica/$id.webm", addedAt = (i + 1) * 1000L)
    }

    private fun store() = MemoryStore(LibrarySnapshot(songs(), listOf(Playlist("p", "Noches", ids, 0)), emptyList(), emptyList()))

    /** El motor simulado, pero con «repetir» que se puede cambiar desde fuera (como la notificación). */
    private class Notification(private val inner: SimulatedEngine = SimulatedEngine()) : AudioEngine by inner {
        var mode = RepeatMode.Off
        override fun setRepeat(mode: RepeatMode) {
            this.mode = mode
            inner.setRepeat(mode)
        }
        override fun snapshot(): EngineSnapshot = inner.snapshot().copy(repeat = mode)
    }

    private fun app(store: MemoryStore = store(), engine: AudioEngine = SimulatedEngine()) =
        AppState(System.currentTimeMillis(), Services(store, SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), engine))

    @Test
    fun playlistsPlayInTheOrderOnScreen() {
        val state = app()
        val p = state.playlist("p")!!
        assertEquals(listOf("s4", "s3", "s2", "s1"), state.playlistSongs(p).map { it.id }, "las últimas añadidas arriba")
        state.playPlaylist("p", shuffle = false)
        assertEquals(listOf("s4", "s3", "s2", "s1"), state.queue.items)

        state.flipPlaylistOrder()
        assertEquals(listOf("s1", "s2", "s3", "s4"), state.playlistSongs(p).map { it.id })

        state.rate("s2", 9)
        state.rate("s3", 5)
        state.sortPlaylistsBy(PlaylistSort.Score)
        assertEquals(listOf("s2", "s3", "s4", "s1"), state.playlistSongs(p).map { it.id }, "por nota; sin nota al final")
    }

    @Test
    fun theOrderIsRemembered() {
        val store = store()
        app(store).sortPlaylistsBy(PlaylistSort.Score)
        assertEquals(PlaylistOrder(PlaylistSort.Score, descending = true), app(store).settings.playlistOrder)
    }

    @Test
    fun addingToAPlaylistKeepsWhen() {
        val store = MemoryStore(LibrarySnapshot(songs(), listOf(Playlist("p", "Noches", listOf("s1"), 0)), emptyList(), emptyList()))
        val state = app(store)
        val before = System.currentTimeMillis()
        state.addToPlaylist("p", listOf("s3", "s1"))
        val p = state.playlist("p")!!
        assertEquals(listOf("s1", "s3"), p.songIds, "sin repetir la que ya estaba")
        assertTrue(p.addedAt.getValue("s3") >= before)
        assertEquals(p, store.load().playlists.single(), "se guarda con la fecha")
        assertEquals(listOf("s3", "s1"), state.playlistSongs(p).map { it.id }, "la recién añadida, arriba")
    }

    @Test
    fun ratingTheSongThatIsPlaying() {
        val state = app()
        state.playFrom(ids, "s2", "NOCHES")
        state.playerOpen = true
        state.rate(state.currentSong!!.id, 7)
        assertEquals(7, state.song("s2")?.score)
        assertEquals(7, state.currentSong?.score)
    }

    @Test
    fun repeatChangedFromTheNotificationReachesTheApp() {
        val engine = Notification()
        val state = app(engine = engine)
        state.playFrom(ids, "s1", "NOCHES")
        assertEquals(RepeatMode.All, state.queue.repeat)
        assertEquals(RepeatMode.All, engine.mode, "lo normal: seguir con la lista")

        engine.mode = RepeatMode.One
        state.step(100)
        assertEquals(RepeatMode.One, state.queue.repeat)

        state.cycleRepeat()
        assertEquals(RepeatMode.All, engine.mode, "y lo de la app llega al reproductor")
        state.step(100)
        assertEquals(RepeatMode.All, state.queue.repeat)

        // Si el reproductor se queda en «no repetir» (de antes), la app le vuelve a poner el suyo.
        engine.mode = RepeatMode.Off
        state.step(100)
        assertEquals(RepeatMode.All, engine.mode)
        assertEquals(RepeatMode.All, state.queue.repeat)
    }

    @Test
    fun aShuffledPlaylistNeverStopsAndIsShuffledAgain() {
        val many = (1..8).map { "s$it" }
        val songs = many.map { Song(it, it, "Artista", durationSec = 60, coverColor = 0, audioPath = "/musica/$it.webm") }
        val state = app(MemoryStore(LibrarySnapshot(songs, listOf(Playlist("p", "Noches", many, 0)), emptyList(), emptyList())))
        state.playPlaylist("p", shuffle = true)
        val firstRound = state.queue.items
        val heard = mutableListOf(state.currentSong!!.id)
        repeat(many.size * 2 - 1) {
            repeat(61) { state.step(1_000) }
            heard += state.currentSong!!.id
        }
        assertTrue(state.isPlaying, "no se para al acabar")
        assertEquals(many.toSet(), heard.take(many.size).toSet(), "primera vuelta: todas una vez")
        assertEquals(many.toSet(), heard.drop(many.size - 1).take(many.size).toSet(), "la vuelta siguiente: todas otra vez")
        // La última de la primera vuelta abre la segunda; las otras siete van en otro orden.
        assertTrue(heard.subList(many.size, many.size * 2 - 1) != firstRound.dropLast(1), "con otro orden")
    }

    @Test
    fun aPlaylistInOrderStartsAgainAtTheEnd() {
        val state = app()
        state.flipPlaylistOrder() // las más antiguas primero: s1, s2, s3, s4
        state.playPlaylist("p", shuffle = false)
        assertEquals("s1", state.currentSong?.id)
        repeat(4) { repeat(61) { state.step(1_000) } }
        assertEquals("s1", state.currentSong?.id, "después de la última, la primera")
        assertTrue(state.isPlaying)
    }
}
