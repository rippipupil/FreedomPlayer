package com.freedomplayer.ui

import com.freedomplayer.core.AppRelease
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Song
import com.freedomplayer.data.LibrarySnapshot
import com.freedomplayer.data.MemoryStore
import com.freedomplayer.data.SampleLyrics
import com.freedomplayer.data.SampleSource
import com.freedomplayer.data.Services
import com.freedomplayer.data.SimulatedDownloader
import com.freedomplayer.data.SimulatedEngine
import com.freedomplayer.data.UpdateService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El botón «atrás» del mini-reproductor y el aviso de versión nueva. */
class RewindAndUpdateTest {
    private val ids = (1..4).map { "s$it" }

    private fun songs() = ids.mapIndexed { i, id ->
        Song(id, "Canción ${i + 1}", "Artista", durationSec = 60, coverColor = 0xFF3B2F8F, audioPath = "/musica/$id.webm")
    }

    private fun store() = MemoryStore(LibrarySnapshot(songs(), listOf(Playlist("p", "Noches", ids, 0xFF7A1F55)), emptyList(), emptyList()))

    private class FakeUpdates(var latest: AppRelease?, val problem: String? = null) : UpdateService {
        val installed = mutableListOf<String>()
        override suspend fun latest(): AppRelease? = latest
        override suspend fun download(release: AppRelease, progress: (Float) -> Unit): String {
            progress(0.5f)
            progress(1f)
            return "/tmp/FreedomPlayer-${release.version}-android.apk"
        }
        override fun install(path: String): String? = problem.also { installed += path }
    }

    private fun app(updates: UpdateService? = null, store: MemoryStore = store()) = AppState(
        System.currentTimeMillis(),
        Services(store, SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), SimulatedEngine(), updates = updates),
    )

    private fun release(v: String) = AppRelease(v, "Notas", "https://example/$v", emptyList())

    // ---------- Atrás ----------

    @Test
    fun oneTapRestartsTheSong() {
        val state = app()
        state.playFrom(ids, "s3", "NOCHES")
        repeat(10) { state.step(1_000) }
        state.rewind(atMillis = 10_000)
        assertEquals("s3", state.currentSong?.id)
        assertEquals(0, state.positionMs)
    }

    @Test
    fun twoQuickTapsGoToThePreviousSongAndMoreKeepGoingBack() {
        val state = app()
        state.playFrom(ids, "s3", "NOCHES")
        repeat(10) { state.step(1_000) }
        state.rewind(atMillis = 10_000)
        state.rewind(atMillis = 10_000 + REWIND_DOUBLE_TAP_MS - 100)
        assertEquals("s2", state.currentSong?.id)
        state.rewind(atMillis = 10_000 + REWIND_DOUBLE_TAP_MS * 2 - 200)
        assertEquals("s1", state.currentSong?.id)
    }

    @Test
    fun slowTapsOnlyRestart() {
        val state = app()
        state.playFrom(ids, "s3", "NOCHES")
        state.rewind(atMillis = 10_000)
        state.rewind(atMillis = 10_000 + REWIND_DOUBLE_TAP_MS + 1)
        assertEquals("s3", state.currentSong?.id)
    }

    @Test
    fun withoutAPreviousSongTheSameOneStartsAgain() {
        val state = app()
        state.playFrom(ids, "s1", "NOCHES")
        repeat(5) { state.step(1_000) }
        state.rewind(atMillis = 5_000)
        state.rewind(atMillis = 5_200)
        assertEquals("s1", state.currentSong?.id)
        assertEquals(0, state.positionMs)
    }

    // ---------- Actualizaciones ----------

    @Test
    fun aNewerReleaseShowsTheNoticeUntilLater() = runBlocking {
        val store = store()
        val state = app(FakeUpdates(release("0.5.0")), store)
        state.appVersion = "0.4.0"
        state.findUpdate(manual = false)
        assertEquals("0.5.0", state.update?.version)
        assertTrue(state.showUpdateNotice)

        state.dismissUpdate()
        assertFalse(state.showUpdateNotice, "«LUEGO» la esconde")
        assertEquals("0.5.0", store.setting("update.dismissed"), "y se recuerda al volver a abrir")
        val reopened = app(FakeUpdates(release("0.5.0")), store).apply { appVersion = "0.4.0" }
        reopened.findUpdate(manual = false)
        assertFalse(reopened.showUpdateNotice)

        reopened.findUpdate(manual = true)
        assertTrue(reopened.showUpdateNotice, "buscando a mano en Ajustes vuelve a salir")
    }

    @Test
    fun sameOrOlderVersionsDoNotAsk() = runBlocking {
        val state = app(FakeUpdates(release("0.4.0")))
        state.appVersion = "0.4.31"
        state.findUpdate(manual = true)
        assertNull(state.update)
        assertFalse(state.showUpdateNotice)
        assertTrue(state.toast?.text?.contains("última versión") == true)
    }

    @Test
    fun devBuildsNeverAsk() = runBlocking {
        val state = app(FakeUpdates(release("9.9.9")))
        state.findUpdate(manual = false)
        assertNull(state.update)
    }

    @Test
    fun updateDownloadsAndOpensTheInstaller() = runBlocking {
        val updates = FakeUpdates(release("0.5.0"))
        val state = app(updates).apply { appVersion = "0.4.0" }
        val loop = launch { state.run() }
        // Deja que el bucle de la app arranque (ahí se lanzan las descargas).
        yield()
        state.findUpdate(manual = false)
        state.installUpdate()
        withTimeout(5_000) { while (updates.installed.isEmpty()) delay(20) }
        assertEquals("/tmp/FreedomPlayer-0.5.0-android.apk", updates.installed.single())
        assertNull(state.updateProgress)
        assertNull(state.error)
        loop.cancel()
    }

    @Test
    fun installProblemsAreExplained() = runBlocking {
        val updates = FakeUpdates(release("0.5.0"), problem = "Permite instalar apps de FreedomPlayer y vuelve a tocar ACTUALIZAR.")
        val state = app(updates).apply { appVersion = "0.4.0" }
        val loop = launch { state.run() }
        // Deja que el bucle de la app arranque (ahí se lanzan las descargas).
        yield()
        state.findUpdate(manual = false)
        state.installUpdate()
        withTimeout(5_000) { while (state.error == null) delay(20) }
        assertEquals("No se pudo instalar la versión 0.5.0", state.error?.title)
        loop.cancel()
    }
}
