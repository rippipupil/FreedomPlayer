package com.freedomplayer.jvm

import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Song
import com.freedomplayer.data.LibraryData
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El móvil y el PC intercambian la biblioteca por la carpeta compartida (como haría Syncthing). */
class SyncExchangeTest {
    private val dir: File = Files.createTempDirectory("fp-sync").toFile()
    private val shared = File(dir, "compartida").apply { mkdirs() }
    private val music = File(dir, "musica").apply { mkdirs() }
    private var clock = 1_000L

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    private fun device(name: String) = SyncExchange(name, shared, music, File(dir, "$name.json")) { clock }

    private fun song(id: String, title: String, score: Int? = null): Song {
        val file = File(music, id.replace(':', '_') + ".m4a").apply { writeText("audio") }
        return Song(id, title, "Ghostline", 180, 0xFF3B2F8F, score = score, audioPath = file.absolutePath, addedAt = 10)
    }

    @Test
    fun bothEndUpWithEverythingAndTheNewestChange() {
        val phone = device("MOVIL")
        val pc = device("PC")
        var phoneLib = LibraryData(listOf(song("yt:a", "Luz de farola", 7)), listOf(Playlist("p1", "Noches", listOf("yt:a"), 0xFF7A1F55)), emptyList(), listOf(PlayEvent("yt:a", 100)))
        var pcLib = LibraryData(listOf(song("local:b", "Mi canción")), emptyList(), listOf(CustomRanking("r1", "Top", listOf("local:b"))), listOf(PlayEvent("local:b", 200)))

        assertNull(phone.exchange(phoneLib), "todavía no ha llegado nada del PC")
        clock = 2_000
        pcLib = pc.exchange(pcLib)!!
        assertEquals(setOf("yt:a", "local:b"), pcLib.songs.map { it.id }.toSet(), "el PC recibe la del móvil (su audio ya está)")
        assertEquals(7, pcLib.songs.first { it.id == "yt:a" }.score)
        assertEquals(listOf("p1"), pcLib.playlists.map { it.id })
        assertEquals(2, pcLib.plays.size)

        // El PC cambia la nota y borra la playlist; luego el móvil cambia el título.
        clock = 3_000
        pcLib = pcLib.copy(songs = pcLib.songs.map { if (it.id == "yt:a") it.copy(score = 9) else it }, playlists = emptyList())
        pc.exchange(pcLib)
        clock = 4_000
        phoneLib = phoneLib.copy(songs = phoneLib.songs.map { it.copy(title = "Luz de farola (en directo)") })
        phoneLib = phone.exchange(phoneLib)!!
        val a = phoneLib.songs.first { it.id == "yt:a" }
        assertEquals("Luz de farola (en directo)", a.title, "el título se cambió después en el móvil")
        assertEquals(9, a.score, "la nota se cambió en el PC después de la del móvil")
        assertTrue(phoneLib.playlists.isEmpty(), "la playlist borrada en el PC no vuelve")
        assertEquals(listOf("r1"), phoneLib.rankings.map { it.id })
        assertEquals(listOf(PlayEvent("yt:a", 100), PlayEvent("local:b", 200)), phoneLib.plays)

        // Y de vuelta al PC: el título nuevo llega y la playlist sigue borrada.
        clock = 5_000
        pcLib = pc.exchange(pcLib)!!
        assertEquals("Luz de farola (en directo)", pcLib.songs.first { it.id == "yt:a" }.title)
        assertTrue(pcLib.playlists.isEmpty())
    }

    @Test
    fun aSongWhoseAudioHasNotArrivedWaits() {
        val phone = device("MOVIL")
        val pc = device("PC")
        val lonely = song("yt:z", "Sin audio todavía")
        phone.exchange(LibraryData(listOf(lonely), emptyList(), emptyList(), emptyList()))
        File(lonely.audioPath!!).delete()
        val got = pc.exchange(LibraryData(emptyList(), emptyList(), emptyList(), emptyList()))!!
        assertTrue(got.songs.isEmpty(), "sin su audio, la canción se queda para la vez siguiente")
        File(lonely.audioPath!!).writeText("ya llegó")
        assertEquals(listOf("yt:z"), pc.exchange(got)!!.songs.map { it.id })
    }
}
