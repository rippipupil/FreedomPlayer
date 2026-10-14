package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Letras en formato LRC, como las da LRCLIB. */
class LrcTest {
    @Test
    fun readsTimesInHundredthsAndThousandths() {
        val lines = Lrc.parse(
            """
            [00:12.34] Primera
            [00:15.5] Segunda
            [01:02.345] Tercera
            [02:00] Cuarta
            """.trimIndent(),
        )
        assertEquals(listOf(12_340L, 15_500L, 62_345L, 120_000L), lines.map { it.atMs })
        assertEquals(listOf("Primera", "Segunda", "Tercera", "Cuarta"), lines.map { it.text })
    }

    @Test
    fun aLineWithSeveralTimesRepeatsAndEverythingIsSorted() {
        val lines = Lrc.parse("[00:30.00][00:10.00] Estribillo\n[00:20.00] Estrofa")
        assertEquals(listOf(10_000L to "Estribillo", 20_000L to "Estrofa", 30_000L to "Estribillo"), lines.map { it.atMs to it.text })
    }

    @Test
    fun skipsTagsAndAppliesTheOffset() {
        val lines = Lrc.parse(
            """
            [ar:Lumen Club]
            [ti:Fantasma rosa]
            [length: 03:21]
            [offset:+500]
            [00:10.00] Hola
            """.trimIndent(),
        )
        assertEquals(listOf(LyricLine(9_500, "Hola")), lines)
        assertEquals(listOf(LyricLine(10_700, "Adiós")), Lrc.parse("[offset:-700]\n[00:10.00]Adiós"))
    }

    @Test
    fun emptyLinesAreBreaksButNotAtTheEnd() {
        val lines = Lrc.parse("[00:01.00] Uno\n[00:05.00] \n[00:09.00] Dos\n[00:20.00]\n[00:21.00]")
        assertEquals(listOf("Uno", Lrc.BREAK, "Dos"), lines.map { it.text })
    }

    @Test
    fun wordTimesFromEnhancedLrcAreRemoved() {
        val lines = Lrc.parse("[00:01.00] <00:01.00>Bajo <00:01.40>la <00:01.80>farola")
        assertEquals("Bajo la farola", lines.single().text)
    }

    @Test
    fun linesWithoutTimeAreIgnoredAndPlainLyricsKeepTheirLines() {
        assertTrue(Lrc.parse("Sin tiempos\nNada").isEmpty())
        val plain = Lrc.plain("\n\nUno\n\nDos\n\n")
        assertEquals(listOf("Uno", "", "Dos"), plain.map { it.text })
        assertTrue(plain.all { it.atMs == LyricLine.UNSYNCED })
        assertTrue(Lrc.isSynced("[00:01.00] a"))
        assertFalse(Lrc.isSynced("a\nb"))
    }

    @Test
    fun lyricsDataPrefersTheSyncedVersion() {
        val both = LyricsData(synced = "[00:01.00] Con tiempo", plain = "Sin tiempo")
        assertEquals("Con tiempo", both.lines().single().text)
        val plainOnly = LyricsData(synced = null, plain = "Sin tiempo")
        assertEquals(LyricLine.UNSYNCED, plainOnly.lines().single().atMs)
        assertEquals(-1, Lyrics.currentIndex(plainOnly.lines(), 60_000), "sin tiempos no se resalta ninguna línea")
        assertTrue(LyricsData(null, " ").isEmpty)
    }

    @Test
    fun currentLineFollowsMilliseconds() {
        val lines = Lrc.parse("[00:01.00] a\n[00:02.50] b\n[00:04.00] c")
        assertEquals(-1, Lyrics.currentIndex(lines, 999))
        assertEquals(0, Lyrics.currentIndex(lines, 1_000))
        assertEquals(0, Lyrics.currentIndex(lines, 2_499))
        assertEquals(1, Lyrics.currentIndex(lines, 2_500))
        assertEquals(2, Lyrics.currentIndex(lines, 90_000))
    }
}
