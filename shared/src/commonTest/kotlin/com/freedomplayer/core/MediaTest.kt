package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Elegir el audio que se descarga, la letra de LRCLIB y los ids de YouTube. */
class MediaTest {
    private fun option(ext: String, kbps: Int, progressive: Boolean = true, original: Boolean = true) =
        AudioOption("https://example.invalid/$ext$kbps", "audio/$ext", ext, kbps * 1000, progressive, original = original)

    @Test
    fun bestAudioIsTheOriginalTrackWithMoreQuality() {
        val opus = option("webm", 160)
        val aac = option("m4a", 128)
        assertEquals(opus, AudioPicker.best(listOf(aac, opus, option("webm", 70))))
        assertEquals(aac, AudioPicker.best(listOf(aac, option("webm", 256, original = false))), "nunca la doblada si hay original")
        assertEquals(aac, AudioPicker.best(listOf(aac, option("webm", 300, progressive = false))), "los manifiestos no se descargan")
        assertEquals(option("m4a", 128), AudioPicker.best(listOf(option("webm", 128), option("m4a", 128))), "empate: m4a")
        assertNull(AudioPicker.best(listOf(option("webm", 160, progressive = false))))
        assertNull(AudioPicker.best(emptyList()))
    }

    @Test
    fun qualityPicksTheTrackClosestToIt() {
        val opus160 = option("webm", 160)
        val aac128 = option("m4a", 128)
        val opus70 = option("webm", 70)
        val opus50 = option("webm", 50)
        val all = listOf(opus70, aac128, opus160, opus50)
        assertEquals(opus160, AudioPicker.pick(all, AudioQuality.Best))
        assertEquals(aac128, AudioPicker.pick(all, AudioQuality.Normal))
        assertEquals(opus50, AudioPicker.pick(all, AudioQuality.Saver))
        assertEquals(AudioPicker.best(all), AudioPicker.pick(all, AudioQuality.DEFAULT), "por defecto, lo de siempre")
        assertEquals(opus160, AudioPicker.pick(listOf(opus160, opus70), AudioQuality.Normal), "Normal: la más cercana a 128 kbps")
        assertEquals(opus70, AudioPicker.pick(listOf(opus70, option("webm", 40, original = false)), AudioQuality.Saver), "nunca la doblada si hay original")
        assertEquals(opus70, AudioPicker.pick(listOf(opus70, option("webm", 30, progressive = false)), AudioQuality.Saver), "los manifiestos no se descargan")
        assertEquals(opus70, AudioPicker.pick(listOf(option("webm", 0), opus70), AudioQuality.Saver), "sin bitrate conocido, al final")
        assertEquals(option("m4a", 48), AudioPicker.pick(listOf(option("webm", 48), option("m4a", 48)), AudioQuality.Saver), "empate: m4a")
        assertNull(AudioPicker.pick(emptyList(), AudioQuality.Saver))
    }

    @Test
    fun folderNamesForTheNotices() {
        assertEquals("FreedomPlayer", folderName("D:\\Música\\FreedomPlayer\\"))
        assertEquals("Música", folderName("/home/cero/Música"))
        assertEquals("D:", folderName("D:\\"))
        assertEquals("/", folderName("/"))
    }

    @Test
    fun savedQualityIsReadBack() {
        for (q in AudioQuality.entries) assertEquals(q, AudioQuality.parse(q.name))
        assertEquals(AudioQuality.Best, AudioQuality.parse(null), "sin ajuste, lo de siempre")
        assertEquals(AudioQuality.Best, AudioQuality.parse("Ultra"))
    }

    private fun lyric(track: String, artist: String, sec: Double, synced: Boolean = true, plain: Boolean = true) =
        LyricsCandidate(track, artist, null, sec, if (synced) "[00:01.00] x" else null, if (plain) "x" else null, false)

    @Test
    fun lyricsMatchArtistAndDurationAndPreferSynced() {
        val right = lyric("Luz de farola", "Ghostline", 167.0)
        val plainOnly = lyric("Luz de farola", "Ghostline", 167.0, synced = false)
        val otherArtist = lyric("Luz de farola", "Otro", 167.0)
        val tooLong = lyric("Luz de farola", "Ghostline", 175.0)
        assertEquals(right, LyricsMatch.best(listOf(plainOnly, otherArtist, tooLong, right), "Luz de farola", "Ghostline", 168))
        assertEquals(plainOnly, LyricsMatch.best(listOf(plainOnly, tooLong), "Luz de farola", "Ghostline", 168))
        assertNull(LyricsMatch.best(listOf(otherArtist, tooLong), "Luz de farola", "Ghostline", 168))
        assertEquals(tooLong, LyricsMatch.best(listOf(tooLong), "Luz de farola", "Ghostline", 0), "sin duración no se filtra por ella")
        assertNull(LyricsMatch.best(listOf(lyric("Luz", "Ghostline", 167.0, synced = false, plain = false)), "Luz", "Ghostline", 167))
    }

    @Test
    fun youtubeIdsComeFromAnyLink() {
        assertEquals("dQw4w9WgXcQ", SongIds.videoIdOf("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM"))
        assertEquals("dQw4w9WgXcQ", SongIds.videoIdOf("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", SongIds.videoIdOf("https://youtu.be/dQw4w9WgXcQ?t=4"))
        assertEquals("yt:dQw4w9WgXcQ", SongIds.fromUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(SongIds.videoIdOf("https://music.youtube.com/playlist?list=OLAK5uy_abc"))
        assertTrue(SongIds.isLocal(SongIds.local("123")))
    }

    @Test
    fun bigNumbersAreShort() {
        assertEquals("950", compactCount(950))
        assertEquals("1,2 k", compactCount(1_234))
        assertEquals("88 k", compactCount(88_000))
        assertEquals("999 k", compactCount(999_400))
        assertEquals("1 M", compactCount(999_600))
        assertEquals("1,2 M", compactCount(1_200_000))
        assertEquals("640 M", compactCount(640_000_000))
        assertEquals("2,1 mil M", compactCount(2_100_000_000))
    }

    @Test
    fun coverColorsAreStablePerId() {
        assertEquals(CoverPalette.forKey("yt:abc"), CoverPalette.forKey("yt:abc"))
        assertTrue(CoverPalette.forKey("yt:xyz") in CoverPalette.colors)
    }
}
