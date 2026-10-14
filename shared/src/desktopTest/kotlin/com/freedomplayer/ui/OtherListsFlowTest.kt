package com.freedomplayer.ui

import com.freedomplayer.core.Playlist
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.Song
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.LibrarySnapshot
import com.freedomplayer.data.MemoryStore
import com.freedomplayer.data.SampleLyrics
import com.freedomplayer.data.SampleSource
import com.freedomplayer.data.Services
import com.freedomplayer.data.SimulatedDownloader
import com.freedomplayer.data.SimulatedEngine
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

/** Listas de otra gente: buscarlas, abrirlas (también por enlace) y añadir canciones sueltas o todas. */
class OtherListsFlowTest {
    private val neon = SampleData.otherLists.first()

    /** Ya tienes «Fantasma rosa» (s5), que está en la lista de ejemplo. */
    private fun store() = MemoryStore(
        LibrarySnapshot(
            listOf(Song("s5", "Fantasma rosa", "Lumen Club", 211, 0xFF7A1F55, audioPath = "/musica/s5.webm")),
            listOf(Playlist("p", "Mías", listOf("s5"), 0xFF7A1F55)),
            emptyList(),
            emptyList(),
        ),
    )

    private fun app(store: MemoryStore = store()) =
        AppState(System.currentTimeMillis(), Services(store, SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), SimulatedEngine()))

    /** Con el bucle de la app en marcha (descargas y listas que se cargan) hasta que se cumpla [done]. */
    private fun AppState.running(action: AppState.() -> Unit, done: AppState.() -> Boolean) = runBlocking {
        val loop = launch { run() }
        try {
            yield()
            action()
            withTimeout(20_000) { while (!done()) delay(20) }
        } finally {
            loop.cancel()
        }
    }

    @Test
    fun theListsTabFindsListsOfOtherPeople() = runBlocking<Unit> {
        val state = app()
        state.search("luz", SearchTab.Playlists)
        assertTrue(state.playlistResults.items.any { it.url == neon.url }, "${state.playlistResults.items}")
        assertNull(state.playlistResults.error)
    }

    @Test
    fun openingAListShowsItsSongsInOrder() {
        val state = app()
        state.running({ openOtherList(neon.url, neon) }) { otherList?.list != null }
        assertEquals(Screen.OtherListDetail(neon.url), state.screen)
        val list = assertNotNull(state.otherList?.list)
        assertEquals(neon.songIds, list.tracks.map { it.id })
        assertEquals("Noche de neón · lo mejor", list.info.title)
    }

    @Test
    fun aSingleSongFromTheListGoesToTheLibraryAndAPlaylist() {
        val state = app()
        state.running({ openOtherList(neon.url, neon) }) { otherList?.list != null }
        val pick = state.otherList!!.list!!.tracks.first { it.id == "c3" }
        state.running({ confirmAdd(AddTarget.Result(pick), "p") }) { song("c3") != null && downloads.isEmpty() }
        assertNotNull(state.song("c3"), "solo esa canción entra en la biblioteca")
        assertEquals(listOf("s5", "c3"), state.playlist("p")?.songIds)
        assertNull(state.song("c5"), "las demás de la lista, no")
    }

    @Test
    fun addAllAddsWhatYouHaveAndDownloadsTheRest() {
        val state = app()
        state.running({ openOtherList(neon.url, neon) }) { otherList?.list != null }
        val list = state.otherList!!.list!!
        state.confirmAdd(AddTarget.WholeList(list), "p")
        val job = assertNotNull(state.otherListJob(neon.url), "se descargan las que faltan")
        assertEquals(neon.songIds - "s5", job.items.map { it.result.id }, "menos la que ya tenías")
        assertTrue(state.addedIds.containsAll(neon.songIds - "s5"))
        state.confirmAdd(AddTarget.WholeList(list), "p")
        assertEquals(1, state.downloads.count { it.albumKey == "list:${neon.url}" }, "pulsarlo otra vez no la repite")
    }

    @Test
    fun aPastedLinkOpensTheListInsteadOfSearching() {
        val state = app()
        val start = System.currentTimeMillis()
        // El buscador espera 350 ms antes de buscar: se deja pasar más que eso.
        state.running({ query = "  ${neon.url}&si=compartido " }) { System.currentTimeMillis() - start > 1_000 }
        assertEquals(neon.url, state.linkedList)
        assertEquals("", state.songResults.query, "un enlace no se busca")
    }

    @Test
    fun aListThatCannotBeOpenedSaysWhyAndRetries() {
        val state = app()
        val missing = "https://www.youtube.com/playlist?list=PLnoexiste"
        state.running({ openOtherList(missing) }) { otherList?.loading == false }
        assertEquals(FailureKind.Unavailable, state.otherList?.error)
        assertNull(state.otherList?.list)
    }
}
