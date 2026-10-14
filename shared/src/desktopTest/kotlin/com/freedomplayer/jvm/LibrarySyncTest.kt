package com.freedomplayer.jvm

import com.freedomplayer.core.PlayEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Juntar la biblioteca del móvil y la del PC: gana el cambio más reciente, lo borrado no vuelve y las escuchas se suman. */
class LibrarySyncTest {
    private fun song(id: String, title: String, at: Long, file: String? = id.replace(':', '_') + ".m4a") =
        SyncSong(id, title, "Ghostline", null, 180, 0xFF3B2F8F, null, 1_000, file, at)

    private val phone = SyncLibrary(
        device = "MOVIL-1",
        exportedAt = 5_000,
        songs = listOf(song("yt:a", "Luz de farola", 100), song("yt:b", "Farolas", 100)),
        scores = listOf(SyncScore("yt:a", 8, 200)),
        playlists = listOf(SyncList("p1", "Noches", listOf("yt:a", "yt:b"), 0xFF7A1F55, 300)),
        plays = listOf(PlayEvent("yt:a", 10), PlayEvent("yt:b", 20)),
    )

    private val pc = SyncLibrary(
        device = "PC-1",
        exportedAt = 6_000,
        songs = listOf(song("yt:a", "Luz de farola (editada)", 400), song("local:c", "Mi canción", 100)),
        scores = listOf(SyncScore("yt:a", 6, 150), SyncScore("local:c", 9, 150)),
        playlists = listOf(SyncList("p1", "Noches en el coche", listOf("yt:a"), 0xFF7A1F55, 250)),
        plays = listOf(PlayEvent("yt:a", 10), PlayEvent("local:c", 30)),
    )

    @Test
    fun theNewestChangeWinsAndNothingIsLost() {
        val merged = LibrarySync.merge(phone, pc)
        assertEquals(listOf("yt:a", "yt:b", "local:c"), merged.songs.map { it.id }, "lo de los dos, primero lo de aquí")
        assertEquals("Luz de farola (editada)", merged.songs.first().title, "el título se cambió después en el PC")
        assertEquals(8, merged.scores.first { it.songId == "yt:a" }.score, "la nota del móvil es más reciente")
        assertEquals(9, merged.scores.first { it.songId == "local:c" }.score)
        assertEquals("Noches", merged.playlists.single().name, "la playlist se cambió después en el móvil")
        assertEquals(listOf("yt:a", "yt:b"), merged.playlists.single().songIds)
        assertEquals(listOf(PlayEvent("yt:a", 10), PlayEvent("yt:b", 20), PlayEvent("local:c", 30)), merged.plays, "las escuchas se suman sin repetir")
        assertEquals("MOVIL-1", merged.device)
        assertEquals(merged, LibrarySync.merge(merged, pc), "juntar otra vez lo mismo no cambia nada")
    }

    @Test
    fun deletedThingsDoNotComeBack() {
        val phoneDeleted = phone.copy(
            songs = phone.songs.filter { it.id != "yt:b" },
            playlists = emptyList(),
            deletedSongs = listOf(Tombstone("yt:b", 500)),
            deletedPlaylists = listOf(Tombstone("p1", 500)),
        )
        val pcWithB = pc.copy(songs = pc.songs + song("yt:b", "Farolas", 100))
        val merged = LibrarySync.merge(pcWithB, phoneDeleted)
        assertFalse(merged.songs.any { it.id == "yt:b" }, "se borró en el móvil después de su último cambio")
        assertTrue(merged.playlists.isEmpty(), "la playlist se borró después de cambiarla")
        assertTrue(merged.plays.none { it.songId == "yt:b" })
        assertEquals(listOf("yt:b"), merged.deletedSongs.map { it.id }, "el borrado se recuerda para los demás")

        // Si en el otro lado se cambió después de borrarla, vuelve.
        val changedLater = pc.copy(playlists = listOf(SyncList("p1", "Noches 2", listOf("yt:a"), null, 900)))
        assertEquals("Noches 2", LibrarySync.merge(phoneDeleted, changedLater).playlists.single().name)
    }

    @Test
    fun theJsonGoesAndComesBackTheSame() {
        val lib = LibrarySync.merge(phone, pc).copy(deletedRankings = listOf(Tombstone("r9", 77)), rankings = listOf(SyncList("r1", "Top", listOf("yt:a"), null, 5)))
        assertEquals(lib, LibrarySync.decode(LibrarySync.encode(lib)))
        assertEquals("biblioteca-MOVIL-1.json", LibrarySync.fileName("MOVIL-1"))
    }

    @Test
    fun whatComesFromOtherDevicesIsChecked() {
        assertFailsWith<IllegalStateException> { LibrarySync.decode("{\"hola\": 1}") }
        assertFailsWith<IllegalArgumentException> { LibrarySync.decode("{\"formato\": 99, \"dispositivo\": \"X\"}") }
        val evil = """{"formato":1,"dispositivo":"X","canciones":[{"id":"yt:z","titulo":"Z","audio":"../../secreto.txt"},{"id":"yt:y","titulo":"Y","audio":"y.m4a"}],"notas":[{"cancion":"yt:y","nota":42,"cambio":1}]}"""
        val lib = LibrarySync.decode(evil)
        assertEquals(null, lib.songs.first { it.id == "yt:z" }.audioFile, "un archivo fuera de la carpeta no se acepta")
        assertEquals("y.m4a", lib.songs.first { it.id == "yt:y" }.audioFile)
        assertEquals(null, lib.scores.single().score, "una nota fuera de 1–10 no se acepta")
        assertFalse(LibrarySync.safeFileName("C:\\Windows\\a.dll"))
    }
}
