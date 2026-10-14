package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Emparejar canciones de Spotify con lo que devuelve YouTube Music de verdad: varios artistas,
 * «feat.», «- Remastered 2009», duraciones que difieren un segundo y versiones que no son la misma.
 */
class SpotifyMatchTest {
    private fun song(id: String, title: String, artist: String, seconds: Int) =
        SearchResult(id, title, artist, seconds, 0xFF3B2F8F, ResultKind.Cancion)

    @Test
    fun sameSongWrittenDifferentlyIsSure() {
        val combi = SpotifyTrack("LA COMBI VERSACE", "ROSALÍA, Tokischa", 162)
        val m = Spotify.match(combi, listOf(song("y1", "LA COMBI VERSACE (feat. Tokischa)", "ROSALÍA", 162)))
        assertEquals(MatchKind.Segura, m.kind, "el «feat.» va en el título en YouTube Music y en los artistas en Spotify")

        val sun = SpotifyTrack("Here Comes The Sun - Remastered 2009", "The Beatles", 185)
        assertEquals(MatchKind.Segura, Spotify.match(sun, listOf(song("y2", "Here Comes The Sun (Remastered 2009)", "The Beatles", 186))).kind)
        assertEquals(MatchKind.Segura, Spotify.match(sun, listOf(song("y3", "Here Comes The Sun", "The Beatles", 186))).kind)

        val malamente = SpotifyTrack("Malamente - Cap.1: Augurio", "ROSALÍA", 150)
        assertEquals("y4", Spotify.match(malamente, listOf(song("y4", "MALAMENTE (Cap.1: Augurio)", "ROSALÍA", 151))).result?.id)
        assertEquals(MatchKind.Segura, Spotify.match(malamente, listOf(song("y4", "MALAMENTE (Cap.1: Augurio)", "ROSALÍA", 151))).kind)
    }

    @Test
    fun otherVersionsAreDoubtful() {
        val sun = SpotifyTrack("Here Comes The Sun", "The Beatles", 185)
        val live = Spotify.match(sun, listOf(song("y1", "Here Comes The Sun (Live)", "The Beatles", 192)))
        assertEquals(MatchKind.Dudosa, live.kind, "«en directo» no es la misma grabación")
        val longer = Spotify.match(sun, listOf(song("y2", "Here Comes The Sun", "The Beatles", 260)))
        assertEquals(MatchKind.Dudosa, longer.kind, "mismo título, pero dura más de un minuto más")
        val cover = Spotify.match(sun, listOf(song("y3", "Here Comes The Sun", "Otra Banda", 186)))
        assertEquals(MatchKind.Dudosa, cover.kind, "mismo título y duración, otro artista: que lo confirme")
        val nothing = Spotify.match(sun, listOf(song("y4", "Yesterday", "The Beatles", 125), song("y5", "Here Comes The Sun", "Otra Banda", 240)))
        assertEquals(MatchKind.SinCoincidencia, nothing.kind)
        assertNull(nothing.result)
    }

    @Test
    fun theSureOneWinsOverTheFirstResult() {
        val track = SpotifyTrack("Malamente", "ROSALÍA", 150)
        val results = listOf(
            song("y1", "Malamente (Live)", "ROSALÍA", 170),
            song("y2", "Malamente", "ROSALÍA", 151),
        )
        val m = Spotify.match(track, results)
        assertEquals(MatchKind.Segura, m.kind)
        assertEquals("y2", m.result?.id)
    }

    @Test
    fun artistsAndQueries() {
        assertEquals(listOf("rosalia", "tokischa"), Spotify.artists("ROSALÍA, Tokischa"))
        assertEquals(listOf("bad bunny", "jhay cortez"), Spotify.artists("Bad Bunny & Jhay Cortez"))
        assertTrue("tyler" in Spotify.artists("Tyler, The Creator feat. Kali Uchis"))
        assertEquals("Here Comes The Sun The Beatles", Spotify.query(SpotifyTrack("Here Comes The Sun - Remastered 2009", "The Beatles", 185)))
        assertEquals("LA COMBI VERSACE ROSALÍA", Spotify.query(SpotifyTrack("LA COMBI VERSACE", "ROSALÍA, Tokischa", 162)))
        assertEquals("Malamente - Cap.1: Augurio ROSALÍA", Spotify.query(SpotifyTrack("Malamente - Cap.1: Augurio", "ROSALÍA", 150)))
    }

    @Test
    fun editorialPlaylistsAreRecognisedByTheirOwner() {
        assertTrue(SpotifyPlaylist("Éxitos España", "Spotify", emptyList()).editorial)
        assertTrue(!SpotifyPlaylist("Noches", "noa", emptyList()).editorial)
        assertTrue(!SpotifyPlaylist("Noches", null, emptyList()).editorial)
    }
}
