package com.freedomplayer.ui

import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Song
import com.freedomplayer.data.LibraryData
import com.freedomplayer.data.MemoryStore
import com.freedomplayer.data.SampleLyrics
import com.freedomplayer.data.SampleSource
import com.freedomplayer.data.Services
import com.freedomplayer.data.SimulatedDownloader
import com.freedomplayer.data.SimulatedEngine
import com.freedomplayer.data.SyncBackend
import com.freedomplayer.data.SyncCount
import com.freedomplayer.jvm.SyncthingBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** «Vincular»: arrancar, el QR del PC, leer el código del otro, «12 de 20» y lo que llega del otro dispositivo. */
class SyncFlowTest {
    private val pcId = "MFZWI3D-BONSGYC-YLTMRWG-C43ENR5-QXGZDMM-FZWI3DP-BONSGYY-LTMRWAD"
    private val phoneId = "AAAAAAA-BBBBBBB-CCCCCCC-DDDDDDD-EEEEEEE-FFFFFFF-GGGGGGG-HHHHHHH"

    private class FakeSync(override val showsQr: Boolean, private val id: String) : SyncBackend {
        var failStart = false
        val paired = mutableListOf<Pair<String, String>>()
        var count: SyncCount? = SyncCount(12, 20)
        var asking: String? = null
        var incoming: LibraryData? = null
        val sent = mutableListOf<LibraryData>()

        override suspend fun start(): String {
            if (failStart) error("no arranca")
            return id
        }

        override fun qr(text: String): List<BooleanArray> = List(21) { BooleanArray(21) { true } }

        override suspend fun pair(otherId: String, otherName: String) {
            paired += otherId to otherName
        }

        override suspend fun pendingDevice(): String? = asking

        override suspend fun progress(otherId: String): SyncCount? = count

        override suspend fun exchange(local: LibraryData): LibraryData? {
            sent += local
            return incoming
        }
    }

    private fun app(backend: SyncBackend?, store: MemoryStore = MemoryStore()) = AppState(
        System.currentTimeMillis(),
        Services(store, SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), SimulatedEngine(), sync = backend),
    )

    /** Con el bucle de la app en marcha durante todo [body]. */
    private fun AppState.live(body: suspend AppState.() -> Unit) = runBlocking {
        val loop = launch { run() }
        try {
            yield()
            body()
        } finally {
            loop.cancel()
        }
    }

    private suspend fun AppState.waitFor(what: String, until: AppState.() -> Boolean) {
        withTimeoutOrNull(10_000) { while (!until()) delay(10) }
            ?: fail("No se llegó a «$what»: fase ${sync.phase}, ${sync.done} de ${sync.total}, error ${sync.error}")
    }

    @Test
    fun thePcShowsItsQrAndThePhonePairsWithIt() {
        val pc = FakeSync(showsQr = true, id = pcId)
        val pcApp = app(pc)
        pcApp.live {
            openSync()
            assertEquals(Screen.Sync, screen)
            waitFor("esperando al móvil") { sync.phase == SyncPhase.Waiting }
            assertEquals(pcId, sync.myId)
            assertNotNull(sync.qr, "el PC enseña su QR")
            assertTrue(pc.paired.isEmpty())
            // El móvil lo escanea e intenta conectar: el PC, que lo está esperando, lo acepta.
            pc.asking = phoneId
            waitFor("vinculado con el móvil") { sync.peer == phoneId && sync.phase == SyncPhase.Syncing }
        }
        assertEquals(listOf(phoneId to "Móvil"), pc.paired)

        val phone = FakeSync(showsQr = false, id = phoneId)
        val phoneApp = app(phone)
        var scanned = false
        phoneApp.qrScanner = { done ->
            scanned = true
            done(pcId.lowercase())
        }
        phoneApp.live {
            openSync()
            waitFor("esperando al PC") { sync.phase == SyncPhase.Waiting }
            assertNull(sync.qr, "el móvil no enseña QR: escanea el del PC")
            scanSyncCode()
            waitFor("12 de 20") { sync.phase == SyncPhase.Syncing && sync.done == 12L }
        }
        assertTrue(scanned)
        assertEquals(listOf(pcId to "PC"), phone.paired)
        assertEquals(20L, phoneApp.sync.total)

        // Al volver a abrir la app ya vinculada, se sincroniza sola hasta acabar.
        phone.count = SyncCount(20, 20)
        phoneApp.live { waitFor("sincronizado") { sync.phase == SyncPhase.Synced } }
        assertEquals(pcId, phoneApp.sync.peer)
    }

    @Test
    fun badCodesAndFailuresAreExplained() {
        val phone = FakeSync(showsQr = false, id = phoneId)
        val phoneApp = app(phone)
        phoneApp.live {
            openSync()
            waitFor("esperando al PC") { sync.phase == SyncPhase.Waiting }
            pairWith("https://example.com/no-es-un-codigo")
            assertTrue(sync.error.orEmpty().startsWith("Ese código no es"), sync.error)
            pairWith(phoneId)
            assertTrue(sync.error.orEmpty().contains("este mismo dispositivo"), sync.error)
        }
        assertTrue(phone.paired.isEmpty())
        assertEquals(SyncPhase.Waiting, phoneApp.sync.phase)

        val broken = FakeSync(showsQr = true, id = pcId).apply { failStart = true }
        val pcApp = app(broken)
        pcApp.live {
            openSync()
            waitFor("el error") { sync.phase == SyncPhase.Failed }
        }
        assertNotNull(pcApp.sync.error)

        val none = app(null)
        none.openSync()
        assertEquals(Screen.Home, none.screen, "sin Syncthing no se abre (y se dice que llegará)")
    }

    @Test
    fun whatComesFromTheOtherDeviceIsApplied() {
        val phone = FakeSync(showsQr = false, id = phoneId)
        val store = MemoryStore()
        val phoneApp = app(phone, store)
        val arrived = Song("yt:nueva", "Llegó del PC", "Ghostline", 200, 0xFF3B2F8F, score = 8, audioPath = "/musica/yt_nueva.m4a")
        phone.incoming = LibraryData(
            songs = phoneApp.songs.toList() + arrived,
            playlists = listOf(Playlist("p-pc", "Del PC", listOf("yt:nueva"), 0xFF7A1F55)),
            rankings = emptyList(),
            plays = emptyList(),
        )
        phoneApp.live {
            pairWith(pcId)
            waitFor("la canción del PC") { songs.any { it.id == "yt:nueva" } }
        }
        assertEquals(8, phoneApp.song("yt:nueva")?.score)
        assertEquals(listOf("Del PC"), phoneApp.playlists.map { it.name })
        assertTrue(phone.sent.isNotEmpty(), "también se manda lo de aquí")
        val saved = store.load()
        assertTrue(saved.songs.any { it.id == "yt:nueva" && it.score == 8 }, "y queda guardado")
        assertEquals(listOf("Del PC"), saved.playlists.map { it.name })
    }

    @Test
    fun aPcThatDoesNotShowUpIsExplainedWithoutGivingUp() {
        // Vinculados, pero el PC no aparece (apagado, sin la app abierta o en otra red).
        val phone = FakeSync(showsQr = false, id = phoneId).apply { count = null }
        val phoneApp = app(phone).apply { syncConnectTimeoutMs = 0 }
        phoneApp.live {
            pairWith(pcId)
            waitFor("el aviso de que no se encuentra el PC") { sync.error.orEmpty().startsWith("No se encuentra el PC") }
            assertEquals(SyncPhase.Syncing, sync.phase, "sigue esperando: no se da por fallido")
            assertFalse(sync.connected)
            // Aparece el PC: se quita el aviso y se ve cuánto lleva.
            phone.count = SyncCount(3, 5)
            waitFor("conectado, 3 de 5") { sync.connected && sync.done == 3L && sync.error == null }
        }
        assertEquals(listOf(pcId to "PC"), phone.paired)
    }

    @Test
    fun withoutTheSyncthingBinaryItSaysSoWithoutCrashing() {
        val dir = Files.createTempDirectory("sin-syncthing").toFile()
        try {
            val missing = SyncthingBackend(
                executable = { null },
                home = dir.resolve("syncthing"),
                music = dir.resolve("musica"),
                library = dir.resolve("sincronizar"),
                showsQr = true,
            )
            val pcApp = app(missing)
            pcApp.live {
                openSync()
                assertEquals(Screen.Sync, screen)
                waitFor("el aviso") { sync.phase == SyncPhase.Failed }
            }
            assertTrue(pcApp.sync.error.orEmpty().contains("no trae Syncthing"), pcApp.sync.error)
        } finally {
            dir.deleteRecursively()
        }
    }
}
