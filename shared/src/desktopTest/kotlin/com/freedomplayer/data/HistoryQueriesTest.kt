package com.freedomplayer.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Ranking
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.Song
import com.freedomplayer.core.Stats
import com.freedomplayer.db.FpDatabase
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La biblioteca guardada en SQLite (la misma base de datos que usa la app) y las consultas del
 * historial: Recientes, Más escuchadas (30 días) y Sin nota.
 */
class HistoryQueriesTest {
    private val day = Stats.DAY_MS
    private val now = 1_800_000_000_000L
    private val drivers = mutableListOf<JdbcSqliteDriver>()

    private fun memoryStore(): SqlLibraryStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FpDatabase.Schema.create(driver)
        drivers += driver
        return SqlLibraryStore(driver)
    }

    @AfterTest
    fun close() {
        drivers.forEach { it.close() }
    }

    private fun song(id: String, title: String, score: Int? = null) =
        Song(id, title, "Artista", 180, 0xFF3B2F8F, score = score, audioPath = "/musica/$id.webm", addedAt = now - 90 * day)

    /** a: 3 escuchas recientes; b: 5 antiguas y 1 reciente; c: 2 recientes; d: nunca. */
    private fun filled(): SqlLibraryStore {
        val store = memoryStore()
        listOf(song("a", "Alba", score = 8), song("b", "Bruma"), song("c", "Calma"), song("d", "Duna")).forEach(store::saveSong)
        val plays = listOf(
            PlayEvent("a", now - 1 * day), PlayEvent("a", now - 2 * day), PlayEvent("a", now - 3 * day),
            PlayEvent("b", now - 40 * day), PlayEvent("b", now - 50 * day), PlayEvent("b", now - 60 * day),
            PlayEvent("b", now - 70 * day), PlayEvent("b", now - 80 * day), PlayEvent("b", now - 10 * day),
            PlayEvent("c", now - 5 * day), PlayEvent("c", now - 1_000),
        )
        plays.forEach(store::addPlay)
        return store
    }

    @Test
    fun recentlyPlayedIsTheLastPlayWithoutRepeats() {
        assertEquals(listOf("c", "a", "b"), filled().recentlyPlayed(10))
        assertEquals(listOf("c", "a"), filled().recentlyPlayed(2))
    }

    @Test
    fun mostPlayedCountsOnlyTheLast30Days() {
        val top = filled().mostPlayedSince(now - 30 * day, 10)
        assertEquals(listOf("a" to 3, "c" to 2, "b" to 1), top)
        assertEquals(listOf("b" to 6, "a" to 3, "c" to 2), filled().mostPlayedSince(0, 10), "de siempre, b gana")
    }

    @Test
    fun unratedMeansPlayedButWithoutScore() {
        val store = filled()
        assertEquals(listOf("b" to 6, "c" to 2), store.unratedPlayed(), "a tiene nota y d no ha sonado nunca")
        store.setScore("b", 5)
        assertEquals(listOf("c" to 2), store.unratedPlayed())
        store.setScore("b", null)
        assertEquals(listOf("b" to 6, "c" to 2), store.unratedPlayed())
    }

    @Test
    fun theDatabaseAgreesWithTheInMemoryRules() {
        val store = filled()
        val snap = store.load()
        val songs = snap.songs
        assertEquals(store.recentlyPlayed(10), Stats.recentlyPlayed(songs, snap.playLog, 10).map { it.id })
        assertEquals(
            store.mostPlayedSince(now - 30 * day, 10),
            Stats.mostPlayedRecently(songs, snap.playLog, now, days = 30, top = 10).map { it.first.id to it.second },
        )
        assertEquals(store.unratedPlayed().map { it.first }, Ranking.unrated(songs).map { it.id })
    }

    @Test
    fun everythingSurvivesAReload() {
        val file = File.createTempFile("freedomplayer", ".db").apply { delete(); deleteOnExit() }
        fun open(): SqlLibraryStore {
            val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties(), FpDatabase.Schema)
            drivers += driver
            return SqlLibraryStore(driver)
        }
        val first = open()
        val song = song("yt:abcdefghijk", "Luz de farola", score = 9).copy(album = "Calles vacías", cover = "/covers/x.jpg", sourceUrl = "https://music.youtube.com/watch?v=abcdefghijk")
        first.saveSong(song)
        first.saveSong(song("local:1", "Archivo propio"))
        first.savePlaylist(Playlist("p1", "Noches", listOf("local:1", "yt:abcdefghijk"), 0xFF7A1F55), 0)
        first.saveRanking(CustomRanking("r1", "Top", listOf("yt:abcdefghijk", "local:1")), 0)
        first.addPlay(PlayEvent("yt:abcdefghijk", now))
        first.saveLyrics("yt:abcdefghijk", LyricsData("[00:01.00] Hola", "Hola"), now)
        first.setSetting("queue.index", "3")

        val again = open().load()
        assertEquals(song.copy(plays = 1), again.songs.first { it.id == song.id })
        assertEquals(listOf("local:1", "yt:abcdefghijk"), again.playlists.single().songIds)
        assertEquals(listOf("yt:abcdefghijk", "local:1"), again.rankings.single().songIds)
        assertEquals(listOf(PlayEvent("yt:abcdefghijk", now)), again.playLog)
        val reopened = open()
        assertEquals("Hola", reopened.lyrics("yt:abcdefghijk")?.lines()?.single()?.text)
        assertEquals(now, reopened.lyricsCheckedAt("yt:abcdefghijk"))
        assertEquals("3", reopened.setting("queue.index"))
    }

    @Test
    fun playlistsKeepTheirOrderAndDeletingASongCleansEverything() {
        val store = filled()
        store.savePlaylist(Playlist("p1", "Uno", listOf("a", "b", "c"), 0xFF7A1F55), 1)
        store.savePlaylist(Playlist("p0", "Cero", listOf("c"), 0xFF3B2F8F), 0)
        store.savePlaylist(Playlist("p1", "Uno", listOf("c", "a"), 0xFF7A1F55), 1)
        store.saveRanking(CustomRanking("r", "Mi top", listOf("a", "c")), 0)
        store.saveLyrics("a", LyricsData("[00:01.00] x", null), now)
        assertEquals(listOf("Cero", "Uno"), store.load().playlists.map { it.name })
        assertEquals(listOf("c", "a"), store.load().playlists.last().songIds)

        store.deleteSong("a")
        val snap = store.load()
        assertTrue(snap.songs.none { it.id == "a" })
        assertTrue(snap.playlists.none { "a" in it.songIds })
        assertTrue(snap.rankings.none { "a" in it.songIds })
        assertTrue(snap.playLog.none { it.songId == "a" })
        assertNull(store.lyrics("a"))
        assertEquals(listOf("c", "b"), store.recentlyPlayed(10))
    }

    @Test
    fun noLyricsIsRememberedSoItIsNotAskedAgainAndAgain() {
        val store = memoryStore()
        assertNull(store.lyricsCheckedAt("x"))
        store.saveLyrics("x", null, now)
        assertNull(store.lyrics("x"))
        assertEquals(now, store.lyricsCheckedAt("x"))
        store.saveLyrics("y", LyricsData(null, null, instrumental = true), now)
        assertEquals(true, store.lyrics("y")?.instrumental)
    }

    @Test
    fun theDemoStoreAnswersLikeTheRealOne() {
        val demo = MemoryStore(LibrarySnapshot(SampleData.songs, SampleData.playlists, SampleData.customRankings, SampleData.playLog(now)))
        val real = memoryStore()
        val snap = demo.load()
        snap.songs.forEach { real.saveSong(it.copy(plays = 0)) }
        snap.playLog.forEach(real::addPlay)
        assertEquals(demo.recentlyPlayed(5), real.recentlyPlayed(5))
        assertEquals(demo.mostPlayedSince(now - 30 * day, 3), real.mostPlayedSince(now - 30 * day, 3))
        assertEquals(demo.unratedPlayed(), real.unratedPlayed())
    }
}
