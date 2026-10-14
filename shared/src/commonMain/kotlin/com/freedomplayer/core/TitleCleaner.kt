package com.freedomplayer.core

/** Título y artista ya limpios. */
data class CleanTitle(val title: String, val artist: String)

/**
 * Limpia los títulos de los vídeos de YouTube al añadirlos como canción:
 * quita «(Official Video)», «[Lyric Video]», «| Official Audio», «M/V», «HD»…, las comillas
 * de «'Título'» y el artista cuando va delante («Ghostline — Luz de farola»), también si son
 * varios («Bad Bunny x Jhay Cortez - Dákiti»). Lo que da información («(en directo)»,
 * «(remix nocturno)», «cover a piano», «ft. …») se queda.
 */
object TitleCleaner {
    /** Palabras que por sí solas convierten un paréntesis en ruido. */
    private val strong = setOf(
        "official", "oficial", "officiel", "ufficiale", "offizielles", "video", "videoclip", "audio", "lyric",
        "lyrics", "letra", "visualizer", "visualiser", "hd", "hq", "4k", "mv", "m/v",
    )

    /** Palabras que acompañan a las anteriores sin aportar nada. */
    private val weak = setOf(
        "music", "musical", "con", "with", "the", "full", "version", "clip", "officiel", "upgrade",
        "y", "and", "en", "in",
    )

    /** Ruido que también se quita cuando va suelto al final, sin paréntesis («… M/V», «… Official Video»). */
    private val trailingPhrases = listOf(
        "official music video", "official video", "official audio", "official lyric video", "official visualizer",
        "lyric video", "music video", "video oficial", "videoclip oficial", "audio oficial", "clip officiel",
        "m/v", "mv", "hd", "hq", "4k",
    )

    private val group = Regex("""\s*[(\[【（]([^)\]】）]*)[)\]】）]""")
    private val dash = Regex("""\s+[-–—]\s+""")
    private val pipes = Regex("""\s+(?:\||//)\s+""")
    private val edges = Regex("""^[\s\-–—|/:·]+|[\s\-–—|/:·]+$""")
    private val spaces = Regex("""\s{2,}""")
    private val artistSeparators = Regex("""\s*(?:,|&|\+|\s[xX×]\s|\sy\s|\sand\s|\s(?:feat\.?|ft\.?|featuring)\s)\s*""")
    private val quotes = listOf("'" to "'", "\"" to "\"", "«" to "»", "‘" to "’", "“" to "”", "「" to "」")

    private fun words(text: String): List<String> =
        Search.normalize(text).replace("m/v", "mv")
            .split(' ', '-', '_', ',', '.', ':', '/', '|', '&', '+')
            .filter { it.isNotBlank() }

    private fun isNoise(text: String): Boolean {
        val w = words(text)
        return w.isNotEmpty() && w.any { it in strong } && w.all { it in strong || it in weak }
    }

    /** Mismo nombre sin fijarse en tildes, mayúsculas, espacios ni signos («LuisFonsi» = «Luis Fonsi»). */
    private fun same(a: String, b: String): Boolean {
        fun key(s: String) = Search.normalize(s).filter { it.isLetterOrDigit() }
        val ka = key(a)
        return ka.isNotEmpty() && ka == key(b)
    }

    /** ¿El trozo de antes del guion es el artista del canal (solo o con otros)? */
    private fun isArtistPart(part: String, artist: String): Boolean =
        same(part, artist) || part.split(artistSeparators).any { same(it, artist) }

    /** «Night Signal - Topic», «LumenClubVEVO» o «Queen Official» → nombre del artista. */
    fun cleanChannel(channel: String): String =
        channel.trim()
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)vevo$"""), "")
            .replace(Regex("""(?i)\s+(official|oficial)$"""), "")
            .trim()

    private fun dropTrailingNoise(text: String): String {
        var t = text.trim()
        var changed = true
        while (changed) {
            changed = false
            val lower = Search.normalize(t)
            if (lower.length != t.length) break
            for (phrase in trailingPhrases) {
                if (lower.endsWith(" $phrase") && t.length > phrase.length + 1) {
                    t = t.dropLast(phrase.length).trimEnd()
                    changed = true
                    break
                }
            }
        }
        return t
    }

    private fun unquote(text: String): String {
        val t = text.trim()
        for ((open, close) in quotes) {
            if (t.length > 2 && t.startsWith(open) && t.endsWith(close) && t.count { it.toString() == open } <= 2) {
                return t.substring(open.length, t.length - close.length).trim()
            }
        }
        return t
    }

    fun clean(rawTitle: String, channel: String): CleanTitle {
        var artist = cleanChannel(channel)
        var title = rawTitle.replace(group) { m -> if (isNoise(m.groupValues[1])) "" else m.value }
        title = title.split(pipes).filterNot { isNoise(it) }.joinToString(" | ")
        title = dropTrailingNoise(title)

        val parts = title.split(dash)
        if (parts.size >= 2 && isArtistPart(parts[0].trim(), artist)) {
            artist = parts[0].trim()
            title = parts.drop(1).joinToString(" - ")
        }
        // «… – Linkin Park» o «… - Official Video» al final: fuera.
        val tail = title.split(dash)
        if (tail.size >= 2 && (same(tail.last(), artist) || isNoise(tail.last()))) {
            title = tail.dropLast(1).joinToString(" - ")
        }
        title = dropTrailingNoise(title)
        title = title.replace(spaces, " ").replace(edges, "").trim()
        title = unquote(title)
        return CleanTitle(title.ifEmpty { rawTitle.trim() }, artist.ifEmpty { channel.trim() })
    }
}
