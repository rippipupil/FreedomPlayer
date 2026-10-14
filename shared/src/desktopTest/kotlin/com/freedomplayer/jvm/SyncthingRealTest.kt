package com.freedomplayer.jvm

import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Song
import com.freedomplayer.data.LibraryData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Dos Syncthing de verdad (el binario oficial que baja la CI, comprobado con su suma SHA-256) en
 * este equipo, haciendo de PC y de móvil: el móvil vincula el id del QR del PC, el PC acepta al
 * móvil que pide conectar, y la música y la biblioteca (canciones, notas y playlists) pasan del
 * PC al móvil. En la CI se ejecuta siempre (falla si falta el binario); en local, con SYNCTHING_BIN.
 */
class SyncthingRealTest {
    private fun binary(): File {
        val bin = System.getenv("SYNCTHING_BIN")?.let(::File)?.takeIf { it.canExecute() }
        if (System.getenv("CI") == "true") assertNotNull(bin, "En la CI tiene que estar Syncthing (SYNCTHING_BIN)")
        assumeTrue("Sin SYNCTHING_BIN: esta prueba es para la CI", bin != null)
        return bin!!
    }

    /** Solo en este equipo: sin descubrimiento global, relés ni NAT, y escuchando en [port]. */
    private suspend fun SyncthingBackend.localOnly(port: Int) {
        val api = assertNotNull(restApi)
        api.patch("/rest/config/options", buildJsonObject {
            put("listenAddresses", buildJsonArray { add(JsonPrimitive("tcp://127.0.0.1:$port")) })
            put("globalAnnounceEnabled", false)
            put("localAnnounceEnabled", false)
            put("relaysEnabled", false)
            put("natEnabled", false)
            put("reconnectionIntervalS", 5)
            put("urAccepted", -1)
            put("crashReportingEnabled", false)
        })
    }

    /** Lo que en casa hace el descubrimiento local: decirle a uno dónde escucha el otro. */
    private suspend fun SyncthingBackend.knows(device: String, port: Int) {
        assertNotNull(restApi).patch("/rest/config/devices/$device", buildJsonObject {
            put("addresses", buildJsonArray { add(JsonPrimitive("tcp://127.0.0.1:$port")) })
        })
    }

    private suspend fun <T : Any> eventually(what: String, logs: () -> String, check: suspend () -> T?): T =
        withTimeoutOrNull(120_000) {
            var value: T? = check()
            while (value == null) {
                delay(1_000)
                value = check()
            }
            value
        } ?: fail("Syncthing no ha llegado a «$what» en 2 minutos.\n${logs()}")

    @Test
    fun aPhoneAndAPcPairAndTheMusicAndLibraryArrive() {
        val exe = binary()
        val root = Files.createTempDirectory("fp-syncthing").toFile()
        fun device(name: String, showsQr: Boolean) = SyncthingBackend(
            executable = { exe },
            home = File(root, "$name/syncthing"),
            music = File(root, "$name/musica").apply { mkdirs() },
            library = File(root, "$name/sincronizar"),
            showsQr = showsQr,
            extraEnvironment = mapOf("HOME" to File(root, name).absolutePath),
        )
        val pc = device("pc", showsQr = true)
        val phone = device("movil", showsQr = false)
        val logs = {
            listOf("pc", "movil").joinToString("\n") { n ->
                "--- $n ---\n" + File(root, "$n/syncthing/syncthing.log").takeIf { it.isFile }?.readLines().orEmpty().takeLast(60).joinToString("\n")
            }
        }
        try {
            runBlocking {
                // En el PC: una canción descargada, con nota, y una playlist con ella.
                val audio = File(root, "pc/musica/yt_abc.m4a").apply { writeBytes(ByteArray(200_000) { (it % 251).toByte() }) }
                val song = Song("yt:abc", "Canción del PC", "Artista", 180, 0xFF3B2F8F, score = 9, audioPath = audio.absolutePath)
                val list = Playlist("p-pc", "Del PC", listOf("yt:abc"), 0xFF7A1F55)

                val pcId = pc.start()
                val phoneId = phone.start()
                assertEquals(pcId, SyncthingIds.device(pcId))
                assertNotEquals(pcId, phoneId)
                val pcPort = SyncthingIds.freePort()
                val phonePort = SyncthingIds.freePort()
                pc.localOnly(pcPort)
                phone.localOnly(phonePort)
                pc.exchange(LibraryData(listOf(song), listOf(list), emptyList(), emptyList()))

                // El móvil escanea el QR del PC y lo vincula; luego intenta conectar con él.
                phone.pair(pcId, "PC")
                phone.knows(pcId, pcPort)
                // El PC, que espera con el QR a la vista, ve al móvil que pide vincularse y lo acepta.
                val asking = eventually("el móvil pide vincularse", logs) { pc.pendingDevice() }
                assertEquals(phoneId, asking)
                pc.pair(phoneId, "Móvil")
                pc.knows(phoneId, phonePort)

                // La música llega al móvil, igual byte a byte.
                val copy = File(root, "movil/musica/yt_abc.m4a")
                eventually("la canción en el móvil", logs) { copy.takeIf { it.isFile && it.length() == audio.length() } }
                assertContentEquals(audio.readBytes(), copy.readBytes())

                // «Sincronizando con el PC 1 de 1»: ya está todo en los dos.
                val count = eventually("todo sincronizado", logs) { phone.progress(pcId)?.takeIf { it.total >= 1 && it.done == it.total } }
                assertEquals(1L, count.total)

                // La biblioteca del PC llega por su carpeta y se junta con la del móvil.
                eventually("la biblioteca del PC en el móvil", logs) {
                    File(root, "movil/sincronizar/" + LibrarySync.fileName(pcId)).takeIf { it.isFile }
                }
                val merged = assertNotNull(phone.exchange(LibraryData(emptyList(), emptyList(), emptyList(), emptyList())))
                val arrived = assertNotNull(merged.songs.singleOrNull { it.id == "yt:abc" })
                assertEquals(9, arrived.score)
                assertEquals(copy.absolutePath, arrived.audioPath)
                assertEquals(listOf("Del PC"), merged.playlists.map { it.name })
            }
        } finally {
            pc.stop()
            phone.stop()
            root.deleteRecursively()
        }
    }
}
