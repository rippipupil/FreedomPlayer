package com.freedomplayer.core

/** Por qué se ordenan las canciones de una playlist. */
enum class PlaylistSort(val label: String) {
    /** Cuándo se añadió a la playlist. */
    Added("Fecha"),

    /** La nota (✦). */
    Score("Nota"),
}

/**
 * Orden de las canciones dentro de las playlists (uno para todas, se guarda en Ajustes). Por
 * defecto, las últimas añadidas arriba.
 */
data class PlaylistOrder(val by: PlaylistSort = PlaylistSort.Added, val descending: Boolean = true) {
    /** Tocar un orden: si ya es el elegido, se da la vuelta; si no, se elige de mayor a menor. */
    fun tap(sort: PlaylistSort): PlaylistOrder = if (sort == by) copy(descending = !descending) else PlaylistOrder(sort, true)

    fun flipped(): PlaylistOrder = copy(descending = !descending)

    /** Para guardarlo: «Added:desc». */
    fun encode(): String = "${by.name}:${if (descending) "desc" else "asc"}"

    /**
     * Las canciones de [playlist] (las que hay en [songs], en el orden en que están guardadas)
     * ordenadas. La fecha de cada una es cuándo entró en la playlist; si no se sabe (las de antes
     * de la 0.6 o las que llegan al sincronizar), cuándo entró en la biblioteca. Con la misma
     * fecha, manda el orden de la playlist. Sin nota van siempre al final, las más nuevas primero.
     */
    fun sort(playlist: Playlist, songs: List<Song>): List<Song> {
        val position = playlist.songIds.withIndex().associate { (i, id) -> id to i }
        fun added(s: Song): Long = playlist.addedAt[s.id]?.takeIf { it > 0 } ?: s.addedAt
        val byDate = compareBy<Song>({ added(it) }, { position[it.id] ?: Int.MAX_VALUE })
        val newestFirst = byDate.reversed()
        return when (by) {
            PlaylistSort.Added -> songs.sortedWith(if (descending) newestFirst else byDate)
            PlaylistSort.Score -> {
                val (rated, unrated) = songs.partition { it.score != null }
                val byScore = if (descending) compareByDescending<Song> { it.score } else compareBy<Song> { it.score }
                rated.sortedWith(byScore.then(newestFirst)) + unrated.sortedWith(newestFirst)
            }
        }
    }

    companion object {
        fun decode(text: String?): PlaylistOrder {
            val parts = text?.split(':') ?: return PlaylistOrder()
            val by = PlaylistSort.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return PlaylistOrder()
            return PlaylistOrder(by, parts.getOrNull(1) != "asc")
        }
    }
}
