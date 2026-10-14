package com.freedomplayer.core

/** Resumen tipo Wrapped de la biblioteca. */
data class LibraryStats(
    val totalPlays: Int,
    val minutes: Int,
    val songCount: Int,
    val ratedCount: Int,
    /** Artistas por reproducciones, de más a menos. */
    val topArtists: List<Pair<String, Int>>,
    /** Canciones más escuchadas. */
    val topSongs: List<Song>,
)

object Stats {
    fun of(songs: List<Song>, top: Int = 5): LibraryStats = LibraryStats(
        totalPlays = songs.sumOf { it.plays },
        minutes = songs.sumOf { it.plays * it.durationSec } / 60,
        songCount = songs.size,
        ratedCount = songs.count { it.score != null },
        topArtists = songs.groupBy { it.artist }
            .map { (artist, list) -> artist to list.sumOf { it.plays } }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            .take(top),
        topSongs = mostPlayed(songs, top),
    )

    /** Más escuchadas de siempre; los empates, por título. */
    fun mostPlayed(songs: List<Song>, top: Int): List<Song> =
        songs.sortedWith(compareByDescending<Song> { it.plays }.thenBy { it.title.lowercase() }).take(top)

    const val DAY_MS: Long = 24L * 60 * 60 * 1000

    /** Escuchas de cada canción desde [sinceMillis]. */
    fun playsSince(log: List<PlayEvent>, sinceMillis: Long): Map<String, Int> =
        log.asSequence().filter { it.atMillis >= sinceMillis }.groupingBy { it.songId }.eachCount()

    /**
     * «Más escuchadas» de Inicio: las de los últimos [days] días, con cuántas veces han sonado.
     * Los empates, por título.
     */
    fun mostPlayedRecently(
        songs: List<Song>,
        log: List<PlayEvent>,
        nowMillis: Long,
        days: Int = 30,
        top: Int = 3,
    ): List<Pair<Song, Int>> {
        val counts = playsSince(log, nowMillis - days * DAY_MS)
        return songs.mapNotNull { s -> counts[s.id]?.let { s to it } }
            .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })
            .take(top)
    }

    /**
     * Resumen de un periodo (Estadísticas): escuchas, minutos, artistas y canciones
     * contando solo lo que ha sonado desde [sinceMillis].
     */
    fun period(songs: List<Song>, log: List<PlayEvent>, sinceMillis: Long, top: Int = 5): LibraryStats {
        val counts = playsSince(log, sinceMillis)
        val played = songs.mapNotNull { s -> counts[s.id]?.let { s to it } }
        return LibraryStats(
            totalPlays = played.sumOf { it.second },
            minutes = played.sumOf { it.second * it.first.durationSec } / 60,
            songCount = songs.size,
            ratedCount = songs.count { it.score != null },
            topArtists = played.groupBy { it.first.artist }
                .map { (artist, list) -> artist to list.sumOf { it.second } }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
                .take(top),
            topSongs = played.sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })
                .take(top)
                .map { it.first },
        )
    }

    /** «Recientes»: lo último que ha sonado, sin repetir, de más reciente a menos. */
    fun recentlyPlayed(songs: List<Song>, log: List<PlayEvent>, limit: Int): List<Song> {
        val byId = songs.associateBy { it.id }
        return log.sortedByDescending { it.atMillis }
            .asSequence()
            .map { it.songId }
            .distinct()
            .mapNotNull { byId[it] }
            .take(limit)
            .toList()
    }
}
