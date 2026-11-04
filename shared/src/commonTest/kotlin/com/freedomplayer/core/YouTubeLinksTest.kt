package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeLinksTest {
    private val id = "PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"

    @Test
    fun playlistLinksOfYouTubeMusic() {
        val music = "https://music.youtube.com/playlist?list=$id"
        assertEquals(music, YouTubeLinks.playlistUrl(music))
        assertEquals(music, YouTubeLinks.playlistUrl("  music.youtube.com/playlist?list=$id&si=abc  "))
        assertEquals(music, YouTubeLinks.playlistUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=$id"))
    }

    @Test
    fun playlistLinksOfYouTube() {
        val yt = "https://www.youtube.com/playlist?list=$id"
        assertEquals(yt, YouTubeLinks.playlistUrl("https://youtube.com/playlist?list=$id&si=XyZ"))
        assertEquals(yt, YouTubeLinks.playlistUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=$id&index=3"))
        assertEquals(yt, YouTubeLinks.playlistUrl("https://youtu.be/dQw4w9WgXcQ?list=$id"))
    }

    @Test
    fun mixesAreOpenedAsTheyAre() {
        val mix = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"
        assertEquals(mix, YouTubeLinks.playlistUrl(mix))
    }

    @Test
    fun anythingElseIsASearch() {
        assertNull(YouTubeLinks.playlistUrl("luz de farola"))
        assertNull(YouTubeLinks.playlistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLinks.playlistUrl("https://open.spotify.com/playlist/1a2B3c4D5e6F7g8H9i0J"))
        assertNull(YouTubeLinks.playlistUrl("https://notyoutube.com/playlist?list=$id"))
        assertNull(YouTubeLinks.playlistUrl("lista de youtube.com/playlist?list=$id"))
    }

    @Test
    fun musicOrVideos() {
        assertTrue(PlaylistResult("a", "b", null, 0, "https://music.youtube.com/playlist?list=$id").isMusic)
        assertFalse(PlaylistResult("a", "b", null, 0, "https://www.youtube.com/playlist?list=$id").isMusic)
    }
}
