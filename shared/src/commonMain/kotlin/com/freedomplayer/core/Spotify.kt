package com.freedomplayer.core

import kotlin.math.abs

/** Lo que se ha pegado en «Pegar playlist de Spotify». */
sealed interface SpotifyLink {
    data class Playlist(val id: String) : SpotifyLink

    /** Playlists de Spotify (editoriales o generadas): no se importan, solo las de usuario. */
    data object Editorial : SpotifyLink

    data object Invalid : SpotifyLink
}

/** Una canción de la playlist de Spotify. */
data class SpotifyTrack(val title: String, val artist: String, val durationSec: Int)

/** Una playlist de Spotify, leída de su página pública. */
data class SpotifyPlaylist(
    val name: String,
    val owner: String?,
    val tracks: List<SpotifyTrack>,
    /** Las canciones que tiene en total, si la página lo dice: sin iniciar sesión puede enseñar menos. */
    val total: Int = tracks.size,
) {
    /** La hace Spotify (editorial): no se importa. */
    val editorial: Boolean get() = owner?.trim().equals("Spotify", ignoreCase = true)
}

enum class MatchKind { Segura, Dudosa, SinCoincidencia }

/** Qué se ha encontrado en YouTube Music para una canción de Spotify. */
data class TrackMatch(val track: SpotifyTrack, val kind: MatchKind, val result: SearchResult?)

object Spotify {
    private val url = Regex("""open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?playlist/([A-Za-z0-9]{10,40})""")
    private val uri = Regex("""spotify:playlist:([A-Za-z0-9]{10,40})""")

    /**
     * Reconoce enlaces «open.spotify.com/playlist/…» y «spotify:playlist:…».
     * Las playlists hechas por Spotify tienen ids que empiezan por «37i9dQZ» (y, al leerlas, su
     * dueño es «Spotify»): así se sabe que no son de un usuario.
     */
    fun parse(text: String): SpotifyLink {
        val t = text.trim()
        val id = url.find(t)?.groupValues?.get(1) ?: uri.find(t)?.groupValues?.get(1) ?: return SpotifyLink.Invalid
        return if (id.startsWith("37i9dQZ")) SpotifyLink.Editorial else SpotifyLink.Playlist(id)
    }

    /** Spotify separa a veces con espacios que no se parten (U+00A0). */
    private fun plain(text: String): String = text.replace(' ', ' ')

    /** Título sin lo que va entre paréntesis ni «- Remastered 2011» y similares. */
    private fun baseTitle(title: String): String =
        Search.normalize(plain(title))
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
            .replace(Regex("""\s+-\s+.*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Lo que acompaña al título: lo de entre paréntesis y lo que va tras « - ». */
    private fun tags(title: String): Set<String> {
        val t = Search.normalize(plain(title))
        val inside = Regex("""[(\[]([^)\]]*)[)\]]""").findAll(t).map { it.groupValues[1] }
        val tail = Regex("""\s+-\s+(.*)$""").find(t.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ").trim())?.groupValues?.get(1)
        return (inside + listOfNotNull(tail)).map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** Lo que no cambia la grabación: «feat. …», «with …», «Remastered 2011», «From "…"». */
    private val neutralTag = Regex("""^(?:feat\.?|ft\.?|featuring|with|con|from)\s.*|.*\bremaster.*""")

    private val stop = setOf("de", "del", "la", "las", "el", "los", "y", "a", "al", "en", "un", "una", "the")

    private fun words(title: String): Set<String> =
        baseTitle(title).split(' ').filter { it.isNotBlank() && it !in stop }.map { Search.stem(it) }.toSet()

    private fun overlap(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size

    private val artistSeparators = Regex("""\s*(?:,|&|;|/|·|\+|\bfeat\.?|\bft\.?|\bfeaturing\b|\bwith\b|\bx\b)\s*""")

    /** Los artistas de «A, B & C feat. D», por separado y normalizados. */
    fun artists(text: String): List<String> =
        Search.normalize(plain(text)).split(artistSeparators).map { it.trim() }.filter { it.isNotEmpty() }

    /** Mismo artista: igual, o con alguno en común («ROSALÍA, Tokischa» y «ROSALÍA»). */
    private fun sameArtist(a: String, b: String): Boolean {
        if (Search.normalize(plain(a)).trim() == Search.normalize(plain(b)).trim()) return true
        val other = artists(b).toSet()
        return artists(a).any { it in other }
    }

    /** La misma grabación por duración (si se sabe): Spotify y YouTube Music difieren uno o dos segundos. */
    private fun sameLength(a: Int, b: Int, slack: Int = 10): Boolean = a <= 0 || b <= 0 || abs(a - b) <= slack

    /** Mismo título, o el mismo con solo «feat.», «Remastered»… de diferencia. */
    private fun sameTitle(a: String, b: String): Boolean {
        if (Search.normalize(plain(a)).trim() == Search.normalize(plain(b)).trim()) return true
        if (baseTitle(a) != baseTitle(b)) return false
        val ta = tags(a)
        val tb = tags(b)
        return ((ta - tb) + (tb - ta)).all { neutralTag.matches(it) }
    }

    /**
     * Busca la canción en el catálogo (solo canciones, no vídeos):
     * - **Segura:** mismo título (o con solo «feat.», «Remastered»… de diferencia), mismo
     *   artista (o alguno en común) y casi la misma duración.
     * - **Dudosa:** mismo artista y un título parecido (otra versión, «en directo», palabras de
     *   más…), o el mismo título con otra duración o con otro artista. Estas se enseñan para
     *   confirmarlas.
     * - **Sin coincidencia:** no hay nada parecido.
     */
    fun match(track: SpotifyTrack, catalog: List<SearchResult>): TrackMatch {
        val songs = catalog.filter { it.kind == ResultKind.Cancion }
        val candidates = songs.filter { sameArtist(track.artist, it.artist) }
        candidates.firstOrNull { sameTitle(track.title, it.title) && sameLength(track.durationSec, it.durationSec) }
            ?.let { return TrackMatch(track, MatchKind.Segura, it) }
        candidates.firstOrNull { sameTitle(track.title, it.title) || baseTitle(it.title) == baseTitle(track.title) }
            ?.let { return TrackMatch(track, MatchKind.Dudosa, it) }
        val trackWords = words(track.title)
        val best = candidates.maxByOrNull { overlap(trackWords, words(it.title)) }
        if (best != null && overlap(trackWords, words(best.title)) >= 0.5) {
            return TrackMatch(track, MatchKind.Dudosa, best)
        }
        // Otro artista (versiones, o un nombre escrito de otra forma) con el mismo título y la misma duración.
        songs.firstOrNull { sameTitle(track.title, it.title) && track.durationSec > 0 && sameLength(track.durationSec, it.durationSec, slack = 3) }
            ?.let { return TrackMatch(track, MatchKind.Dudosa, it) }
        return TrackMatch(track, MatchKind.SinCoincidencia, null)
    }

    fun matchAll(tracks: List<SpotifyTrack>, catalog: List<SearchResult>): List<TrackMatch> =
        tracks.map { match(it, catalog) }

    /**
     * Lo que se busca en YouTube Music por cada canción: el título (sin «- Remastered 2011»,
     * que YouTube Music no suele poner) y el primer artista.
     */
    fun query(track: SpotifyTrack): String {
        val title = plain(track.title).replace(Regex("""\s+-\s+[^-]*remaster[^-]*$""", RegexOption.IGNORE_CASE), "").trim()
        val artist = plain(track.artist).split(',', '&').first().trim()
        return "$title $artist".trim()
    }
}
