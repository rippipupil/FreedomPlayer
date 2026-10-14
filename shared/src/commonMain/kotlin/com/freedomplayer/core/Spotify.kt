package com.freedomplayer.core

/** Lo que se ha pegado en «Pegar playlist de Spotify». */
sealed interface SpotifyLink {
    data class Playlist(val id: String) : SpotifyLink

    /** Playlists de Spotify (editoriales o generadas): no se importan, solo las de usuario. */
    data object Editorial : SpotifyLink

    data object Invalid : SpotifyLink
}

/** Una canción de la playlist de Spotify. */
data class SpotifyTrack(val title: String, val artist: String, val durationSec: Int)

enum class MatchKind { Segura, Dudosa, SinCoincidencia }

/** Qué se ha encontrado en YouTube Music para una canción de Spotify. */
data class TrackMatch(val track: SpotifyTrack, val kind: MatchKind, val result: SearchResult?)

object Spotify {
    private val url = Regex("""open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?playlist/([A-Za-z0-9]{10,40})""")
    private val uri = Regex("""spotify:playlist:([A-Za-z0-9]{10,40})""")

    /**
     * Reconoce enlaces «open.spotify.com/playlist/…» y «spotify:playlist:…».
     * Las playlists hechas por Spotify tienen ids que empiezan por «37i9dQZ»; mientras no se
     * consulte su API, esa es la forma de saber que no son de un usuario.
     */
    fun parse(text: String): SpotifyLink {
        val t = text.trim()
        val id = url.find(t)?.groupValues?.get(1) ?: uri.find(t)?.groupValues?.get(1) ?: return SpotifyLink.Invalid
        return if (id.startsWith("37i9dQZ")) SpotifyLink.Editorial else SpotifyLink.Playlist(id)
    }

    /** Título sin lo que va entre paréntesis ni «- Remastered 2011» y similares. */
    private fun baseTitle(title: String): String =
        Search.normalize(title)
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
            .replace(Regex("""\s+-\s+.*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val stop = setOf("de", "del", "la", "las", "el", "los", "y", "a", "al", "en", "un", "una", "the")

    private fun words(title: String): Set<String> =
        baseTitle(title).split(' ').filter { it.isNotBlank() && it !in stop }.map { Search.stem(it) }.toSet()

    private fun overlap(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size

    /**
     * Busca la canción en el catálogo (solo canciones, no vídeos):
     * - **Segura:** mismo título y artista.
     * - **Dudosa:** mismo artista y un título parecido (otra versión, «en directo», palabras de más…).
     *   Estas se enseñan para confirmarlas.
     * - **Sin coincidencia:** no hay nada parecido.
     */
    fun match(track: SpotifyTrack, catalog: List<SearchResult>): TrackMatch {
        val artist = Search.normalize(track.artist)
        val candidates = catalog.filter { it.kind == ResultKind.Cancion && Search.normalize(it.artist) == artist }
        candidates.firstOrNull { Search.normalize(it.title) == Search.normalize(track.title) }
            ?.let { return TrackMatch(track, MatchKind.Segura, it) }
        candidates.firstOrNull { baseTitle(it.title) == baseTitle(track.title) }
            ?.let { return TrackMatch(track, MatchKind.Dudosa, it) }
        val trackWords = words(track.title)
        val best = candidates.maxByOrNull { overlap(trackWords, words(it.title)) }
        return if (best != null && overlap(trackWords, words(best.title)) >= 0.5) {
            TrackMatch(track, MatchKind.Dudosa, best)
        } else {
            TrackMatch(track, MatchKind.SinCoincidencia, null)
        }
    }

    fun matchAll(tracks: List<SpotifyTrack>, catalog: List<SearchResult>): List<TrackMatch> =
        tracks.map { match(it, catalog) }
}
