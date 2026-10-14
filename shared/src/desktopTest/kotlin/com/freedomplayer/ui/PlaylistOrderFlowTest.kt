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
        assertEquals(RepeatMode.Off, state.queue.repeat)

        engine.mode = RepeatMode.All
        state.step(100)
        assertEquals(RepeatMode.All, state.queue.repeat)

        state.cycleRepeat()
        assertEquals(RepeatMode.One, engine.mode, "y lo de la app llega al reproductor")
        state.step(100)
        assertEquals(RepeatMode.One, state.queue.repeat)
    }
}
