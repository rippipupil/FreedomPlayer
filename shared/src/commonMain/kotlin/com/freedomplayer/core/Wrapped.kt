package com.freedomplayer.core

/** El periodo de un Wrapped: un mes ([month] de 1 a 12) o un año entero ([month] null). */
data class WrappedPeriod(val year: Int, val month: Int? = null) {
    fun contains(m: YearMonth): Boolean = m.year == year && (month == null || m.month == month)

    /** «OCTUBRE 2026» o «2026». */
    val label: String get() = month?.let { YearMonth(year, it).label } ?: year.toString()

    /** «OCT 26» o «2026», para los chips. */
    val short: String get() = month?.let { "${YearMonth(year, it).short} ${year.mod(100).toString().padStart(2, '0')}" } ?: year.toString()

    /** «tu octubre» o «tu 2026». */
    val title: String get() = "Tu " + (month?.let { YearMonth(year, it).name } ?: year.toString())
}

/** A qué hora escuchas sobre todo: la «personalidad» del Wrapped. */
enum class ListeningTime(val label: String, val blurb: String, val hours: IntRange) {
    Madrugada("FANTASMA DE MADRUGADA", "Lo tuyo es cuando todo el mundo duerme.", 0..5),
    Manana("PÁJARO MADRUGADOR", "La música te despierta antes que el café.", 6..11),
    Tarde("ALMA DE TARDE", "Tus tardes tienen banda sonora.", 12..19),
    Noche("CRIATURA DE LA NOCHE", "Cuando se encienden los neones, suenas tú.", 20..23),
    ;

    companion object {
        fun of(hour: Int): ListeningTime = entries.first { hour in it.hours }
    }
}

/** El resumen de un periodo, tarjeta a tarjeta. */
data class WrappedReport(
    val period: WrappedPeriod,
    val plays: Int,
    val minutes: Int,
    /** Canciones distintas que han sonado. */
    val songs: Int,
    /** Artistas distintos que han sonado. */
    val artists: Int,
    /** Las más escuchadas, con sus escuchas (hasta 5). */
    val topSongs: List<Pair<Song, Int>>,
    /** Los artistas más escuchados, con sus escuchas (hasta 5). */
    val topArtists: List<Pair<String, Int>>,
    /** Las que sonaron por primera vez en el periodo. */
    val newSongs: Int,
    /** Escuchas por hora del día (24 valores, hora local). */
    val byHour: List<Int>,
    val time: ListeningTime,
    /** La de más nota de las que han sonado (los empates, la que más ha sonado). */
    val bestRated: Song?,
    /** Días seguidos escuchando, la racha más larga del periodo. */
    val streakDays: Int,
)

object Wrapped {
    /** Los periodos con escuchas: cada año (del más reciente) seguido de sus meses (también del más reciente). */
    fun periods(log: List<PlayEvent>, offsetAt: (Long) -> Long = { 0L }): List<WrappedPeriod> {
        val months = Stats.months(log, offsetAt)
        return months.map { it.year }.distinct().flatMap { y ->
            listOf(WrappedPeriod(y)) + months.filter { it.year == y }.map { WrappedPeriod(y, it.month) }
        }
    }

    /** El resumen de [period], o null si en él no ha sonado nada de la biblioteca. */
    fun report(songs: List<Song>, log: List<PlayEvent>, period: WrappedPeriod, offsetAt: (Long) -> Long = { 0L }): WrappedReport? {
        val byId = songs.associateBy { it.id }
        val hour = 60L * 60 * 1000
        val counts = HashMap<String, Int>()
        val byHour = IntArray(24)
        val days = HashSet<Long>()
        val first = HashMap<String, Long>()
        var newSongs = 0
        for (e in log) {
            val before = first[e.songId]
            if (before == null || e.atMillis < before) first[e.songId] = e.atMillis
        }
        for (e in log) {
            if (e.songId !in byId) continue
            val offset = offsetAt(e.atMillis)
            if (!period.contains(YearMonth.of(e.atMillis, offset))) continue
            val local = e.atMillis + offset
            counts[e.songId] = (counts[e.songId] ?: 0) + 1
            byHour[(local.mod(Stats.DAY_MS) / hour).toInt()]++
            days += local.floorDiv(Stats.DAY_MS)
        }
        if (counts.isEmpty()) return null
        for ((id, at) in first) {
            if (id in counts && period.contains(YearMonth.of(at, offsetAt(at)))) newSongs++
        }
        val played = counts.map { (id, n) -> byId.getValue(id) to n }
        val byPlays = compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() }
        val artists = played.groupBy { it.first.artist }
            .map { (artist, list) -> artist to list.sumOf { it.second } }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        val time = ListeningTime.entries.maxBy { t -> t.hours.sumOf { byHour[it] } }
        return WrappedReport(
            period = period,
            plays = counts.values.sum(),
            minutes = played.sumOf { it.second * it.first.durationSec } / 60,
            songs = counts.size,
            artists = artists.size,
            topSongs = played.sortedWith(byPlays).take(5),
            topArtists = artists.take(5),
            newSongs = newSongs,
            byHour = byHour.toList(),
            time = time,
            bestRated = played.filter { it.first.score != null }
                .sortedWith(compareByDescending<Pair<Song, Int>> { it.first.score }.then(byPlays))
                .firstOrNull()?.first,
            streakDays = GhostCollection.longestRun(days),
        )
    }
}
