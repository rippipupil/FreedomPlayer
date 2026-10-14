package com.freedomplayer.core

/** Lo que lleva puesto un fantasma coleccionable. */
enum class GhostAccessory { None, Headphones, Crown, Shades, Nightcap, PartyHat, Halo }

/**
 * Los fantasmas coleccionables: cada uno se gana escuchando (o puntuando, o con la biblioteca) y,
 * una vez ganado, se puede poner como el fantasma de la app. Los colores son los del cuerpo, la
 * cara y los mofletes (null = los del fantasma rosa de siempre).
 */
enum class GhostSkin(
    val label: String,
    val hint: String,
    /** Cuánto hay que llegar a tener de lo que cuenta [GhostCollection.progress]. */
    val goal: Int,
    val accessory: GhostAccessory = GhostAccessory.None,
    val body: Long? = null,
    val ink: Long? = null,
    val cheek: Long? = null,
) {
    Clasico("Clásico", "El de siempre.", 0),
    Menta("Menta", "Escucha 50 canciones.", 50, body = 0xFF8FE3CB, ink = 0xFF1E5C50, cheek = 0xFF2F9C82),
    Lavanda("Lavanda", "Pon nota a 10 canciones.", 10, body = 0xFFC7C2FF, ink = 0xFF3B2F8F, cheek = 0xFF8E87F0),
    Rey("Rey", "Pon 5 ✦ (un 10) a una canción.", 1, GhostAccessory.Crown),
    Coleccionista("Coleccionista", "Ten 50 canciones en tu biblioteca.", 50, GhostAccessory.Shades),
    Buho("Búho", "Escucha 10 canciones entre las 0 y las 5 de la madrugada.", 10, GhostAccessory.Nightcap, body = 0xFFA9B2EE, ink = 0xFF1E1A4A, cheek = 0xFF5B57D9),
    Constante("Constante", "Escucha música 7 días seguidos.", 7, GhostAccessory.Halo),
    Fiesta("Fiesta", "Abre tu Wrapped.", 1, GhostAccessory.PartyHat),
    Dj("DJ", "Escucha 250 canciones.", 250, GhostAccessory.Headphones),
    Dorado("Dorado", "Escucha 1000 canciones.", 1000, GhostAccessory.Crown, body = 0xFFFFD27A, ink = 0xFF7A4A00, cheek = 0xFFFF9F43),
}

/** Lo que cuenta para ganar fantasmas, sacado de la biblioteca y del historial. */
data class GhostFacts(
    val plays: Int = 0,
    val rated: Int = 0,
    val perfectTens: Int = 0,
    val librarySize: Int = 0,
    val nightPlays: Int = 0,
    val bestStreakDays: Int = 0,
    val wrappedSeen: Boolean = false,
)

object GhostCollection {
    /** Cuánto llevas de lo que pide [skin] (se compara con [GhostSkin.goal]). */
    fun progress(skin: GhostSkin, f: GhostFacts): Int = when (skin) {
        GhostSkin.Clasico -> 0
        GhostSkin.Menta, GhostSkin.Dj, GhostSkin.Dorado -> f.plays
        GhostSkin.Lavanda -> f.rated
        GhostSkin.Rey -> f.perfectTens
        GhostSkin.Coleccionista -> f.librarySize
        GhostSkin.Buho -> f.nightPlays
        GhostSkin.Constante -> f.bestStreakDays
        GhostSkin.Fiesta -> if (f.wrappedSeen) 1 else 0
    }

    fun isUnlocked(skin: GhostSkin, f: GhostFacts): Boolean = progress(skin, f) >= skin.goal

    fun unlocked(f: GhostFacts): Set<GhostSkin> = GhostSkin.entries.filter { isUnlocked(it, f) }.toSet()

    /**
     * Lo que cuenta, a partir de la biblioteca y del historial. [offsetAt] da la diferencia con UTC
     * de cada momento (para saber la hora y el día de cada escucha en tu zona).
     */
    fun facts(songs: List<Song>, log: List<PlayEvent>, wrappedSeen: Boolean, offsetAt: (Long) -> Long = { 0L }): GhostFacts {
        val hour = 60L * 60 * 1000
        var night = 0
        val days = HashSet<Long>()
        for (e in log) {
            val local = e.atMillis + offsetAt(e.atMillis)
            if (local.mod(Stats.DAY_MS) / hour < 5) night++
            days += local.floorDiv(Stats.DAY_MS)
        }
        return GhostFacts(
            plays = log.size,
            rated = songs.count { it.score != null },
            perfectTens = songs.count { it.score == Ranking.MAX_SCORE },
            librarySize = songs.size,
            nightPlays = night,
            bestStreakDays = longestRun(days),
            wrappedSeen = wrappedSeen,
        )
    }

    /** La racha más larga de días seguidos en [days] (días desde 1970). */
    fun longestRun(days: Set<Long>): Int {
        var best = 0
        for (d in days) {
            if (d - 1 in days) continue
            var n = 1
            while (d + n in days) n++
            if (n > best) best = n
        }
        return best
    }
}
