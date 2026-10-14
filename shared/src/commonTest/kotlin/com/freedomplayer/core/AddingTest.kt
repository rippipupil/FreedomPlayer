package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Añadir desde el buscador: títulos de vídeo, Spotify y avisos de descarga. */
class AddingTest {
    @Test
    fun videoTitlesLoseTheNoiseButKeepTheVersion() {
        assertEquals(
            CleanTitle("Luz de farola (en directo)", "Ghostline"),
            TitleCleaner.clean("Ghostline — Luz de farola (en directo)", "Ghostline"),
        )
        assertEquals(
            CleanTitle("Fantasma rosa (en directo)", "Lumen Club"),
            TitleCleaner.clean("Lumen Club - Fantasma rosa (en directo) (Official Music Video)", "Lumen Club"),
        )
        assertEquals(
            CleanTitle("Luz de farola (remix nocturno)", "Canal de fans"),
            TitleCleaner.clean("Luz de farola (remix nocturno) | Official Audio", "Canal de fans"),
        )
        assertEquals(
            CleanTitle("Neón de medianoche (slowed + reverb)", "nocturna fm"),
            TitleCleaner.clean("Neón de medianoche (slowed + reverb) [Lyric Video]", "nocturna fm"),
        )
        assertEquals(
            CleanTitle("Callejón 04 — cover acústico", "Sofía Rueda"),
            TitleCleaner.clean("Callejón 04 — cover acústico (Video Oficial) HD", "Sofía Rueda"),
        )
        assertEquals(CleanTitle("Señal perdida", "Night Signal"), TitleCleaner.clean("Señal perdida (Audio)", "Night Signal - Topic"))
    }

    @Test
    fun aVideoEntersTheLibraryWithTheCleanTitle() {
        val video = SampleData.catalog.first { it.id == "v4" }
        val song = video.toLibrarySong()
        assertEquals("Fantasma rosa (en directo)", song.title)
        assertEquals("Lumen Club", song.artist)
        assertNull(song.score)
        assertEquals(0, song.plays)
    }

    @Test
    fun spotifyLinksAreRecognised() {
        assertEquals(
            SpotifyLink.Playlist("1a2B3c4D5e6F7g8H9i0J"),
            Spotify.parse("https://open.spotify.com/playlist/1a2B3c4D5e6F7g8H9i0J?si=abc123"),
        )
        assertEquals(SpotifyLink.Playlist("1a2B3c4D5e6F7g8H9i0J"), Spotify.parse("  spotify:playlist:1a2B3c4D5e6F7g8H9i0J "))
        assertEquals(SpotifyLink.Playlist("1a2B3c4D5e6F7g8H9i0J"), Spotify.parse("https://open.spotify.com/intl-es/playlist/1a2B3c4D5e6F7g8H9i0J"))
        assertEquals(SpotifyLink.Editorial, Spotify.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals(SpotifyLink.Invalid, Spotify.parse("https://open.spotify.com/album/1a2B3c4D5e6F7g8H9i0J"))
        assertEquals(SpotifyLink.Invalid, Spotify.parse("luz de farola"))
    }

    @Test
    fun spotifyMatchesAreSureDoubtfulOrMissing() {
        val byTitle = Spotify.matchAll(SampleData.spotifyTracks, SampleData.catalog).associateBy { it.track.title }
        assertEquals(MatchKind.Segura, byTitle.getValue("Fantasma Rosa").kind)
        assertEquals("s5", byTitle.getValue("Fantasma Rosa").result?.id)
        assertEquals(MatchKind.Segura, byTitle.getValue("Señal Perdida").kind)
        assertEquals(MatchKind.Dudosa, byTitle.getValue("Luces de la ciudad").kind)
        assertEquals("c3", byTitle.getValue("Luces de la ciudad").result?.id)
        assertEquals(MatchKind.Dudosa, byTitle.getValue("Té de jazmín (Live)").kind)
        assertEquals(MatchKind.SinCoincidencia, byTitle.getValue("Lluvia en Tokio").kind)
        assertNull(byTitle.getValue("Lluvia en Tokio").result)
    }

    @Test
    fun downloadNoticeCountsSongs() {
        assertEquals("¡Añadida! Descargando y buscando letra…", downloadMessage(0, 1))
        assertEquals("Descargando 3 de 12", downloadMessage(2, 12))
        assertEquals("Descargando 12 de 12", downloadMessage(12, 12))
    }

    @Test
    fun searchFindsPluralsAndWordStarts() {
        val titles = Search.results(SampleData.catalog, "luz de farola", ResultKind.Cancion).map { it.title }
        assertTrue("Luces de ciudad" in titles, "«luces» cuenta como «luz»")
        assertTrue(Search.results(SampleData.catalog, "kao", ResultKind.Cancion).all { it.artist == "Kaori Vale" })
        assertEquals(3, Search.results(SampleData.catalog, "luz de farola", ResultKind.Video).size)
    }
}
