package com.freedomplayer.core

/** Relleno de cada ✦ al mostrar una nota. */
enum class StarFill { Full, Half, Empty }

object Ranking {
    const val MAX_SCORE = 10
    const val STARS = 5

    /**
     * Ranking general: solo canciones con nota, de mayor a menor.
     * Empates: gana la que más veces has escuchado; después, por título.
     */
    fun general(songs: List<Song>): List<Song> =
        songs.filter { it.score != null }
            .sortedWith(
                compareByDescending<Song> { it.score }
                    .thenByDescending { it.plays }
                    .thenBy { it.title.lowercase() }
            )

    /**
     * «Sin nota»: las canciones ya escuchadas alguna vez que todavía no tienen nota,
     * primero las que más escuchas. Las que nunca han sonado no aparecen.
     */
    fun unrated(songs: List<Song>): List<Song> =
        songs.filter { it.score == null && it.plays > 0 }
            .sortedWith(compareByDescending<Song> { it.plays }.thenBy { it.title.lowercase() })

    /** Cómo se dibujan las 5 estrellas para una nota de 1 a 10 (null = todas vacías). */
    fun stars(score: Int?): List<StarFill> {
        val s = (score ?: 0).coerceIn(0, MAX_SCORE)
        return List(STARS) { i ->
            when (s - i * 2) {
                in 2..Int.MAX_VALUE -> StarFill.Full
                1 -> StarFill.Half
                else -> StarFill.Empty
            }
        }
    }

    /**
     * Nota al tocar una ✦: la mitad izquierda da media estrella y la derecha la estrella entera.
     * [starIndex] va de 0 a 4.
     */
    fun scoreFromTap(starIndex: Int, leftHalf: Boolean): Int {
        val i = starIndex.coerceIn(0, STARS - 1)
        return i * 2 + if (leftHalf) 1 else 2
    }

    /** «4,5» para una nota de 9. */
    fun label(score: Int): String {
        val whole = score / 2
        return if (score % 2 == 0) "$whole" else "$whole,5"
    }

    /** Mueve una canción dentro de un ranking personal (arrastrar para ordenar). */
    fun move(ranking: CustomRanking, from: Int, to: Int): CustomRanking {
        val ids = ranking.songIds
        if (from !in ids.indices || to !in ids.indices || from == to) return ranking
        val list = ids.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return ranking.copy(songIds = list)
    }

    /** Puntúa una canción y devuelve la biblioteca actualizada. */
    fun rate(songs: List<Song>, songId: String, score: Int?): List<Song> {
        val clean = score?.coerceIn(1, MAX_SCORE)
        return songs.map { if (it.id == songId) it.copy(score = clean) else it }
    }
}
