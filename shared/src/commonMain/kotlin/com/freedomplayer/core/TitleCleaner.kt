package com.freedomplayer.core

/** Título y artista ya limpios. */
data class CleanTitle(val title: String, val artist: String)

/**
 * Limpia los títulos de los vídeos de YouTube al añadirlos como canción:
 * quita «(Official Video)», «[Lyric Video]», «| Official Audio», «HD»…, y el artista
 * cuando va delante («Ghostline — Luz de farola»). Lo que da información
 * («(en directo)», «(remix nocturno)», «cover a piano») se queda.
 */
object TitleCleaner {
    /** Palabras que por sí solas convierten un paréntesis en ruido. */
    private val strong = setOf(
        "official", "oficial", "video", "videoclip", "audio", "lyric", "lyrics", "letra",
        "visualizer", "visualiser", "hd", "hq", "4k", "mv", "m/v",
    )

    /** Palabras que acompañan a las anteriores sin aportar nada. */
    private val weak = setOf("music", "musical", "con", "with", "the", "full", "version", "clip", "officiel")

    private val group = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    private val dash = Regex("""\s+[-–—]\s+""")
    private val pipes = Regex("""\s+(?:\||//)\s+""")
    private val trailingNoise = Regex("""(?i)(?:\s+(?:hd|hq|4k))+\s*$""")
    private val edges = Regex("""^[\s\-–—|/:·]+|[\s\-–—|/:·]+$""")
    private val spaces = Regex("""\s{2,}""")

    private fun isNoise(text: String): Boolean {
        val words = Search.normalize(text).split(' ', '-', '_', ',', '.', ':').filter { it.isNotBlank() }
        return words.isNotEmpty() && words.any { it in strong } && words.all { it in strong || it in weak }
    }

    /** «Night Signal - Topic» o «LumenClubVEVO» → nombre del artista. */
    fun cleanChannel(channel: String): String =
        channel.trim()
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)vevo$"""), "")
            .replace(Regex("""(?i)\s+(official|oficial)$"""), "")
            .trim()

    fun clean(rawTitle: String, channel: String): CleanTitle {
        var artist = cleanChannel(channel)
        var title = rawTitle.replace(group) { m -> if (isNoise(m.groupValues[1])) "" else m.value }
        title = title.split(pipes).filterNot { isNoise(it) }.joinToString(" | ")
        title = title.replace(trailingNoise, "")

        val parts = title.split(dash, limit = 2)
        if (parts.size == 2 && Search.normalize(parts[0].trim()) == Search.normalize(artist)) {
            artist = parts[0].trim()
            title = parts[1]
        }
        title = title.replace(spaces, " ").replace(edges, "").trim()
        return CleanTitle(title.ifEmpty { rawTitle.trim() }, artist.ifEmpty { channel.trim() })
    }
}
