package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryLogicTest {
    private fun song(id: String, title: String, artist: String, plays: Int, dur: Int = 120, score: Int? = null) =
        Song(id, title, artist, dur, 0xFF000000, plays = plays, score = score)

    @Test
    fun searchIgnoresAccentsAndCase() {
        val hits = Search.results(SampleData.catalog, "NEON medianoche", ResultKind.Cancion)
        assertEquals("Neón de medianoche", hits.first().title)
    }

    @Test
    fun searchRanksByMatchedWordsLikeTheDesign() {
        val titles = Search.results(SampleData.catalog, "luz de farola", ResultKind.Cancion).map { it.title }
        assertEquals(listOf("Luz de farola", "Luz de farola (versión acústica)"), titles.take(2))
        assertTrue("La luz que queda" in titles && "Farolas" in titles)
        assertTrue(Search.results(SampleData.catalog, "farolillo", ResultKind.Cancion).isEmpty())
    }

    @Test
    fun searchSeparatesSongsAndVideos() {
        val songs = Search.results(SampleData.catalog, "luz de farola", ResultKind.Cancion)
        val videos = Search.results(SampleData.catalog, "luz de farola", ResultKind.Video)
        assertTrue(songs.isNotEmpty() && songs.all { it.kind == ResultKind.Cancion })
        assertTrue(videos.isNotEmpty() && videos.all { it.kind == ResultKind.Video })
        assertTrue(Search.results(SampleData.catalog, "   ", ResultKind.Cancion).isEmpty())
    }

    @Test
    fun searchMarksWhatIsAlreadyInTheLibrary() {
        val library = SampleData.songs
        val luz = SampleData.catalog.first { it.title == "Luz de farola" }
        val acoustic = SampleData.catalog.first { it.title == "Luz de farola (versión acústica)" }
        assertTrue(Search.inLibrary(luz, library))
        assertFalse(Search.inLibrary(acoustic, library))
    }

    @Test
    fun albumsMatchByTitleArtistOrSong() {
        val byArtist = Search.albums(SampleData.albums, SampleData.catalog, "kaori")
        assertEquals(listOf("Neón"), byArtist.map { it.title })
        val bySong = Search.albums(SampleData.albums, SampleData.catalog, "luz de farola")
        assertTrue(bySong.any { it.title == "Calles vacías" })
    }

    @Test
    fun statsSumPlaysMinutesAndTopArtists() {
        val songs = listOf(
            song("a", "Uno", "Ana", plays = 10, dur = 180, score = 8),
            song("b", "Dos", "Bea", plays = 4, dur = 120),
            song("c", "Tres", "Ana", plays = 1, dur = 60),
        )
        val s = Stats.of(songs)
        assertEquals(15, s.totalPlays)
        assertEquals((10 * 180 + 4 * 120 + 60) / 60, s.minutes)
        assertEquals(listOf("Ana" to 11, "Bea" to 4), s.topArtists)
        assertEquals("a", s.topSongs.first().id)
        assertEquals(1, s.ratedCount)
    }

    @Test
    fun lyricsFollowThePosition() {
        val lines = SampleData.lyricsFor("s5")
        assertEquals(-1, Lyrics.currentIndex(lines, 10))
        assertEquals(2, Lyrics.currentIndex(lines, 84))
        assertEquals(1, Lyrics.windowStart(lines, 2))
        assertEquals(2, Lyrics.windowStart(lines, 5), "al final se ven las 4 últimas")
        assertEquals(0, Lyrics.windowStart(lines, -1))
    }

    @Test
    fun greetingDependsOnTheHour() {
        assertEquals("BUENOS DÍAS", greetingFor(9))
        assertEquals("BUENAS TARDES", greetingFor(17))
        assertEquals("BUENAS NOCHES", greetingFor(23))
        assertEquals("BUENAS NOCHES", greetingFor(3))
    }

    @Test
    fun songCountUsesSingular() {
        assertEquals("1 CANCIÓN", songCount(1))
        assertEquals("6 CANCIONES", songCount(6))
    }

    @Test
    fun mostPlayedCountsOnlyTheLast30Days() {
        val day = Stats.DAY_MS
        val now = 100 * day
        val songs = listOf(song("a", "Uno", "Ana", plays = 50), song("b", "Dos", "Bea", plays = 3))
        val log = List(50) { PlayEvent("a", now - 40 * day) } + List(3) { PlayEvent("b", now - 2 * day) }
        assertEquals(listOf("b" to 3), Stats.mostPlayedRecently(songs, log, now).map { it.first.id to it.second })
    }

    @Test
    fun periodStatsOnlyCountThePeriod() {
        val day = Stats.DAY_MS
        val now = 100 * day
        val songs = listOf(song("a", "Uno", "Ana", plays = 5, dur = 120), song("b", "Dos", "Bea", plays = 1, dur = 60, score = 7))
        val log = listOf(PlayEvent("a", now - 1 * day), PlayEvent("a", now - 2 * day), PlayEvent("a", now - 90 * day), PlayEvent("b", now - 3 * day))
        val s = Stats.period(songs, log, now - 30 * day)
        assertEquals(3, s.totalPlays)
        assertEquals((2 * 120 + 60) / 60, s.minutes)
        assertEquals(listOf("Ana" to 2, "Bea" to 1), s.topArtists)
        assertEquals(listOf("a", "b"), s.topSongs.map { it.id })
        assertEquals(1, s.ratedCount)
    }

    @Test
    fun sampleHistoryMatchesTheDesign() {
        val now = 1_800_000_000_000L
        val log = SampleData.playLog(now)
        val most = Stats.mostPlayedRecently(SampleData.songs, log, now)
        assertEquals(listOf("Fantasma rosa" to 84, "Neón de medianoche" to 61, "Luz de farola" to 47), most.map { it.first.title to it.second })
        val recent = Stats.recentlyPlayed(SampleData.songs, log, 6).map { it.title }
        assertEquals(listOf("Neón de medianoche", "Fantasma rosa", "Callejón 04", "Luz de farola", "Desaparecer", "Ahora no"), recent)
        SampleData.songs.forEach { s -> assertEquals(s.plays, log.count { it.songId == s.id }, s.title) }
    }

    @Test
    fun sampleRankingMatchesTheDesign() {
        val top = Ranking.general(SampleData.songs).take(3).map { it.title }
        assertEquals(listOf("Fantasma rosa", "Neón de medianoche", "Callejón 04"), top)
        assertEquals(listOf("Bajo la farola", "Farolas"), Ranking.unrated(SampleData.songs).map { it.title })
    }
}
