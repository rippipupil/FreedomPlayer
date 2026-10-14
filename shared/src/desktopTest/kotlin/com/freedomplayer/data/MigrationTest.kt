package com.freedomplayer.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.Playlist
import com.freedomplayer.db.FpDatabase
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Al actualizar la app no se pierde nada: una biblioteca guardada con la versión 1 de la base de
 * datos (la de la 0.5 y anteriores) se abre con la de ahora y lo conserva todo.
 */
class MigrationTest {
    private val dir: File = Files.createTempDirectory("fp-migracion").toFile()
    private val file = File(dir, "freedomplayer.db")
    private val url = "jdbc:sqlite:${file.absolutePath}"

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    /** Las tablas tal y como las creaba la versión 1 (Library.sq hasta la 0.5). */
    private val v1 = listOf(
        """CREATE TABLE song (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT,
            duration_sec INTEGER NOT NULL, cover_color INTEGER NOT NULL, score INTEGER, audio_path TEXT, cover TEXT,
            source_url TEXT, added_at INTEGER NOT NULL)""",
        "CREATE TABLE playlist (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, cover_color INTEGER NOT NULL, position INTEGER NOT NULL)",
        "CREATE TABLE playlist_song (playlist_id TEXT NOT NULL, song_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, position))",
        "CREATE TABLE ranking (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)",
        "CREATE TABLE ranking_song (ranking_id TEXT NOT NULL, song_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (ranking_id, position))",
        "CREATE TABLE play_event (song_id TEXT NOT NULL, at_millis INTEGER NOT NULL)",
        "CREATE INDEX play_event_song ON play_event(song_id)",
        "CREATE INDEX play_event_time ON play_event(at_millis)",
        "CREATE TABLE lyrics (song_id TEXT NOT NULL PRIMARY KEY, synced TEXT, plain TEXT, instrumental INTEGER NOT NULL DEFAULT 0, checked_at INTEGER NOT NULL)",
        "CREATE TABLE setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
    )

    /** Una biblioteca de la 0.5: dos canciones con nota, una playlist, un ranking, escuchas, letra y ajustes. */
    private fun writeVersion1() {
        DriverManager.getConnection(url).use { c ->
            c.createStatement().use { st ->
                v1.forEach(st::execute)
                st.execute("INSERT INTO song VALUES ('yt:a', 'Alba', 'Artista', 'Disco', 200, 1, 9, '/m/a.m4a', NULL, 'https://y/a', 1000)")
                st.execute("INSERT INTO song VALUES ('yt:b', 'Bruma', 'Otra', NULL, 180, 2, 4, '/m/b.m4a', 'https://c/b.jpg', NULL, 2000)")
                st.execute("INSERT INTO playlist VALUES ('p1', 'Noches', 77, 0)")
                st.execute("INSERT INTO playlist_song VALUES ('p1', 'yt:b', 0)")
                st.execute("INSERT INTO playlist_song VALUES ('p1', 'yt:a', 1)")
                st.execute("INSERT INTO ranking VALUES ('r1', 'Top gym', 0)")
                st.execute("INSERT INTO ranking_song VALUES ('r1', 'yt:a', 0)")
                st.execute("INSERT INTO play_event VALUES ('yt:a', 5000)")
                st.execute("INSERT INTO play_event VALUES ('yt:a', 6000)")
                st.execute("INSERT INTO lyrics VALUES ('yt:a', '[00:01.00]Hola', NULL, 0, 7000)")
                st.execute("INSERT INTO setting VALUES ('download.quality', 'Normal')")
                st.execute("PRAGMA user_version = 1")
            }
        }
    }

    private fun open(): Pair<JdbcSqliteDriver, SqlLibraryStore> {
        val driver = JdbcSqliteDriver(url, Properties(), FpDatabase.Schema)
        return driver to SqlLibraryStore(driver)
    }

    private fun userVersion(): Long = DriverManager.getConnection(url).use { c ->
        c.createStatement().use { st -> st.executeQuery("PRAGMA user_version").use { it.next(); it.getLong(1) } }
    }

    @Test
    fun aVersion1LibraryKeepsEverything() {
        writeVersion1()
        val (driver, store) = open()
        val lib = store.load()
        driver.close()

        assertEquals(FpDatabase.Schema.version, userVersion(), "queda en la versión de ahora")
        assertEquals(listOf("yt:a" to 9, "yt:b" to 4), lib.songs.sortedBy { it.id }.map { it.id to it.score })
        assertEquals(listOf("/m/a.m4a", "/m/b.m4a"), lib.songs.sortedBy { it.id }.map { it.audioPath })
        assertEquals(2, lib.songs.first { it.id == "yt:a" }.plays)

        val p = lib.playlists.single()
        assertEquals("Noches", p.name)
        assertEquals(listOf("yt:b", "yt:a"), p.songIds, "el orden de la playlist no cambia")
        assertEquals(mapOf("yt:b" to 2000L, "yt:a" to 1000L), p.addedAt, "la fecha de cada una: cuándo entró en la biblioteca")

        assertEquals(listOf("yt:a"), lib.rankings.single().songIds)
        assertEquals(listOf(PlayEvent("yt:a", 5000), PlayEvent("yt:a", 6000)), lib.plays)

        val (again, reopened) = open()
        assertEquals("[00:01.00]Hola", reopened.lyrics("yt:a")?.synced)
        assertEquals("Normal", reopened.setting("download.quality"))
        again.close()
    }

    @Test
    fun theNewDatesAreSavedAndOpeningAgainIsHarmless() {
        writeVersion1()
        val (driver, store) = open()
        val p = store.load().playlists.single()
        store.savePlaylist(p.copy(songIds = p.songIds + "yt:c", addedAt = p.addedAt + ("yt:c" to 9000L)), 0)
        driver.close()

        // Segunda vez que se abre: ya está en la versión nueva y no se migra otra vez.
        val (again, reopened) = open()
        val saved: Playlist = reopened.load().playlists.single()
        again.close()
        assertEquals(listOf("yt:b", "yt:a", "yt:c"), saved.songIds)
        assertEquals(9000L, saved.addedAt["yt:c"])
        assertEquals(2000L, saved.addedAt["yt:b"])
    }
}
