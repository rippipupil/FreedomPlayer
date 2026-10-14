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
    fun period(songs: List<Song>, log: List<PlayEvent>, sinceMillis: Long, top: Int = 5): LibraryStats =
        summary(songs, playsSince(log, sinceMillis), top)

    /** Resumen a partir de cuántas veces ha sonado cada canción ([counts], por id). */
    fun summary(songs: List<Song>, counts: Map<String, Int>, top: Int = 5): LibraryStats {
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

    // ---------- Por meses (tipo Wrapped) ----------

    /** Escuchas de cada canción en [month] (con la hora local: [offsetAt] da la diferencia con UTC). */
    fun playsIn(log: List<PlayEvent>, month: YearMonth, offsetAt: (Long) -> Long = { 0L }): Map<String, Int> =
        log.asSequence().filter { YearMonth.of(it.atMillis, offsetAt(it.atMillis)) == month }.groupingBy { it.songId }.eachCount()

    /** Los meses con alguna escucha, del más reciente al más antiguo. */
    fun months(log: List<PlayEvent>, offsetAt: (Long) -> Long = { 0L }): List<YearMonth> =
        log.asSequence().map { YearMonth.of(it.atMillis, offsetAt(it.atMillis)) }.distinct().sortedDescending().toList()

    /** Escuchas por mes de los [count] meses que acaban en [last] (del más antiguo al más reciente, con ceros). */
    fun playsPerMonth(log: List<PlayEvent>, last: YearMonth, count: Int = 12, offsetAt: (Long) -> Long = { 0L }): List<Pair<YearMonth, Int>> {
        val byMonth = log.groupingBy { YearMonth.of(it.atMillis, offsetAt(it.atMillis)) }.eachCount()
        return (count - 1 downTo 0).map { back -> last.plus(-back).let { m -> m to (byMonth[m] ?: 0) } }
    }

    /** Las que suenan por primera vez en [month] (la primera escucha de todas cae en ese mes). */
    fun discoveredIn(log: List<PlayEvent>, month: YearMonth, offsetAt: (Long) -> Long = { 0L }): Set<String> {
        val first = HashMap<String, Long>()
        for (e in log) {
            val before = first[e.songId]
            if (before == null || e.atMillis < before) first[e.songId] = e.atMillis
        }
        return first.filter { (_, at) -> YearMonth.of(at, offsetAt(at)) == month }.keys
    }

    /**
     * Lo más destacado de tu ranking en un periodo ([counts]: escuchas de cada canción): tu
     * n.º 1, la que más suena con nota, la que más suena sin nota, tu artista mejor puntuado y,
     * si se da [discovered], el descubrimiento del periodo.
     */
    fun highlights(songs: List<Song>, counts: Map<String, Int>, discovered: Set<String> = emptySet()): List<Highlight> {
        val out = ArrayList<Highlight>()
        fun times(n: Int) = if (n == 1) "1 vez" else "$n veces"
        val best = Ranking.general(songs).firstOrNull()
        if (best != null) {
            val n = counts[best.id] ?: 0
            out += Highlight("TU N.º 1 DEL RANKING", best.title, "${starsText(best.score!!)} · " + if (n > 0) "sonó ${times(n)}" else "esta vez no ha sonado", best.id)
        }
        val byPlays = compareByDescending<Song> { counts[it.id] ?: 0 }.thenBy { it.title.lowercase() }
        val rated = songs.filter { it.score != null && (counts[it.id] ?: 0) > 0 }.sortedWith(byPlays).firstOrNull()
        if (rated != null && rated.id != best?.id) {
            out += Highlight("LA QUE MÁS SUENA CON NOTA", rated.title, "${starsText(rated.score!!)} · ${times(counts[rated.id] ?: 0)}", rated.id)
        }
        val unrated = songs.filter { it.score == null && (counts[it.id] ?: 0) > 0 }.sortedWith(byPlays).firstOrNull()
        if (unrated != null) {
            out += Highlight("SIN NOTA Y SIN PARAR", unrated.title, "${times(counts[unrated.id] ?: 0)} y aún sin nota", unrated.id)
        }
        val artist = songs.filter { it.score != null }
            .groupBy { it.artist }
            .map { (name, list) -> Triple(name, list.map { it.score!! }.average(), list.size) }
            .filter { it.third >= 2 }
            .sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first.lowercase() })
            .firstOrNull()
        if (artist != null) {
            val avg = kotlin.math.round(artist.second).toInt().coerceIn(1, Ranking.MAX_SCORE)
            out += Highlight("TU ARTISTA MEJOR PUNTUADO", artist.first, "Media de ${starsText(avg)} en ${artist.third} canciones")
        }
        val found = songs.filter { it.id in discovered && (counts[it.id] ?: 0) > 0 }.sortedWith(byPlays).firstOrNull()
        if (found != null) {
            out += Highlight("DESCUBRIMIENTO", found.title, "La primera vez que sonó · ${times(counts[found.id] ?: 0)} este mes", found.id)
        }
        return out
    }
}

/** Una tarjeta de «lo más destacado» de Estadísticas. */
data class Highlight(val label: String, val title: String, val detail: String, val songId: String? = null)

/** Un mes del calendario (para Estadísticas por meses). */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int = index.compareTo(other.index)

    private val index: Int get() = year * 12 + (month - 1)

    fun plus(months: Int): YearMonth {
        val i = index + months
        return YearMonth(i.floorDiv(12), i.mod(12) + 1)
    }

    /** «OCTUBRE 2026». */
    val label: String get() = "${NAMES[month - 1].uppercase()} $year"

    /** «octubre». */
    val name: String get() = NAMES[month - 1]

    /** «OCT». */
    val short: String get() = NAMES[month - 1].take(3).uppercase()

    companion object {
        private val NAMES = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )

        /** El mes de [millis], con la hora local a [offsetMillis] de UTC. */
        fun of(millis: Long, offsetMillis: Long = 0): YearMonth {
            // Días desde 1970 → fecha (el algoritmo de los días civiles de Howard Hinnant).
            val days = (millis + offsetMillis).floorDiv(Stats.DAY_MS)
            val z = days + 719_468
            val era = z.floorDiv(146_097L)
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val m = if (mp < 10) mp + 3 else mp - 9
            val y = yoe + era * 400 + if (m <= 2) 1 else 0
            return YearMonth(y.toInt(), m.toInt())
        }
    }
}
