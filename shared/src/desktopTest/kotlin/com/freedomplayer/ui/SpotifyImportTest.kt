package com.freedomplayer.ui

import com.freedomplayer.core.MatchKind
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.SpotifyPlaylist
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.MemoryStore
import com.freedomplayer.data.MusicSource
import com.freedomplayer.data.SampleLyrics
import com.freedomplayer.data.SampleSource
import com.freedomplayer.data.Services
import com.freedomplayer.data.SimulatedDownloader
import com.freedomplayer.data.SimulatedEngine
import com.freedomplayer.data.SourceException
import com.freedomplayer.data.SpotifyReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Importar de Spotify fuera de los datos de ejemplo: se lee la playlist, cada canción se busca en
 * el buscador, se crea la playlist con lo seguro y lo dudoso queda para confirmar; y si algo
 * falla (Spotify bloquea, la playlist es privada o editorial, no se puede buscar), se dice claro.
 */
class SpotifyImportTest {
    private val link = "https://open.spotify.com/playlist/1a2B3c4D5e6F7g8H9i0J?si=abc123"

    private class FakeSpotify : SpotifyReader {
        var fail: FailureKind? = null
        var list = SpotifyPlaylist("Noches en el coche", "noa", SampleData.spotifyTracks, total = 12)
        val asked = mutableListOf<String>()

        override suspend fun playlist(id: String): SpotifyPlaylist {
            asked += id
            fail?.let { throw SourceException(it, "prueba") }
            return list
        }
    }

    /** Un buscador que no puede buscar (YouTube pide comprobar que no eres un robot). */
    private class BlockedSource : MusicSource by SampleSource(latencyMs = 0) {
        override suspend fun searchSongs(query: String): List<SearchResult> = throw SourceException(FailureKind.Blocked, "bot")
    }

    private fun app(spotify: SpotifyReader?, source: MusicSource = SampleSource(latencyMs = 0)) = AppState(
        System.currentTimeMillis(),
        Services(MemoryStore(), source, SimulatedDownloader(), SampleLyrics(), SimulatedEngine(), spotify = spotify),
    )

    /** Con el bucle de la app en marcha: pega el enlace, importa y espera a que acabe. */
    private fun AppState.import(text: String) = runBlocking {
        val loop = launch { run() }
        try {
            yield()
            spotify.link = text
            startSpotifyImport()
            withTimeout(10_000) { while (spotify.phase == ImportPhase.Matching) delay(10) }
        } finally {
            loop.cancel()
        }
    }

    @Test
    fun aPublicPlaylistIsReadMatchedAndCreated() {
        val reader = FakeSpotify()
        val state = app(reader)
        state.import(link)
        val sp = state.spotify
        assertEquals(listOf("1a2B3c4D5e6F7g8H9i0J"), reader.asked)
        assertEquals(ImportPhase.Done, sp.phase)
        assertNull(sp.error)
        assertEquals(SampleData.spotifyTracks, sp.matches.map { it.track }, "en el orden de Spotify")
        assertEquals(9, sp.checked)
        assertEquals(6, sp.matches.count { it.kind == MatchKind.Segura })
        assertEquals(2, sp.matches.count { it.kind == MatchKind.Dudosa })
        assertEquals(1, sp.matches.count { it.kind == MatchKind.SinCoincidencia })
        val playlist = assertNotNull(state.playlist(sp.playlistId))
        assertEquals("Noches en el coche", playlist.name, "con el nombre de Spotify")
        assertEquals(6, state.addedIds.size, "lo seguro ya se descarga")
        assertTrue(sp.note.orEmpty().contains("9 de las 12"), "Spotify enseña menos de las que tiene: ${sp.note}")

        // Confirmar una dudosa la añade también.
        val doubtful = sp.matches.indexOfFirst { it.kind == MatchKind.Dudosa }
        state.decideMatch(doubtful, true)
        assertEquals(7, state.addedIds.size)
    }

    @Test
    fun saysClearlyWhyItCannotImport() {
        val reader = FakeSpotify()
        val state = app(reader)
        reader.fail = FailureKind.Blocked
        state.import(link)
        assertEquals(ImportPhase.Paste, state.spotify.phase)
        assertTrue(state.spotify.error.orEmpty().contains("sin iniciar sesión"), state.spotify.error)

        reader.fail = FailureKind.Unavailable
        state.import(link)
        assertTrue(state.spotify.error.orEmpty().contains("privada"), state.spotify.error)

        reader.fail = FailureKind.Changed
        state.import(link)
        assertTrue(state.spotify.error.orEmpty().contains("ha cambiado"), state.spotify.error)

        reader.fail = null
        reader.list = SpotifyPlaylist("Éxitos España", "Spotify", SampleData.spotifyTracks)
        state.import(link)
        assertTrue(state.spotify.error.orEmpty().contains("la hace Spotify"), "las editoriales no se importan")
        assertTrue(state.playlists.isEmpty())

        val blocked = app(FakeSpotify(), BlockedSource())
        blocked.import(link)
        assertEquals(ImportPhase.Paste, blocked.spotify.phase)
        assertTrue(blocked.spotify.error.orEmpty().startsWith("No se ha podido buscar en YouTube Music"), blocked.spotify.error)
        assertTrue(blocked.playlists.isEmpty(), "sin nada encontrado no se crea la playlist")

        val none = app(null)
        none.import(link)
        assertEquals(ImportPhase.Paste, none.spotify.phase)
        assertNotNull(none.spotify.error)
    }
}
