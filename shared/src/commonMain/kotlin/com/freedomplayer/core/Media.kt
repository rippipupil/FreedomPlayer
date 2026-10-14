package com.freedomplayer.core

/** Ids de canción: «yt:<id del vídeo>» para lo que viene de YouTube y «local:<…>» para los archivos propios. */
object SongIds {
    private val videoId = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})""")

    fun youtube(videoId: String): String = "yt:$videoId"

    fun local(key: String): String = "local:$key"

    /** El id del vídeo de una dirección de YouTube o YouTube Music, o null. */
    fun videoIdOf(url: String): String? = videoId.find(url)?.groupValues?.get(1)

    /** El id de canción de una dirección de YouTube («yt:…»), o null. */
    fun fromUrl(url: String): String? = videoIdOf(url)?.let(::youtube)

    fun isLocal(id: String): Boolean = id.startsWith("local:")
}

/** Una de las pistas de audio que ofrece YouTube para un vídeo. */
data class AudioOption(
    val url: String,
    /** «audio/webm», «audio/mp4»… */
    val mimeType: String,
    /** Extensión del archivo: «webm», «m4a»… */
    val extension: String,
    /** Bits por segundo (la media si se conoce). */
    val bitrate: Int,
    /** Se descarga de una vez por HTTP (no es un manifiesto DASH/HLS). */
    val progressive: Boolean,
    /** Bytes; -1 si no se sabe. */
    val contentLength: Long = -1,
    /** Pista original (no doblada ni audiodescrita). */
    val original: Boolean = true,
    /** Cabeceras que pide YouTube para esta dirección (User-Agent del cliente que la dio…). */
    val headers: Map<String, String> = emptyMap(),
)

object AudioPicker {
    /**
     * La mejor pista para guardar o escuchar: solo las que se pueden bajar de una vez, primero la
     * original (no doblada) y luego la de más calidad. Si empatan, m4a, que es la que más
     * reproductores entienden.
     */
    fun best(options: List<AudioOption>): AudioOption? =
        options.asSequence()
            .filter { it.progressive && it.url.isNotBlank() }
            .sortedWith(
                compareByDescending<AudioOption> { it.original }
                    .thenByDescending { it.bitrate }
                    .thenBy { if (it.extension == "m4a") 0 else 1 },
            )
            .firstOrNull()
}

/** Una letra encontrada en LRCLIB. */
data class LyricsCandidate(
    val track: String,
    val artist: String,
    val album: String?,
    /** Segundos (LRCLIB los da con decimales). */
    val durationSec: Double,
    val synced: String?,
    val plain: String?,
    val instrumental: Boolean,
)

object LyricsMatch {
    /** Diferencia de duración que se acepta, en segundos (LRCLIB usa ±2; se deja algo más para los vídeos). */
    const val TOLERANCE_SEC = 3.0

    private fun key(s: String) = Search.normalize(s).filter { it.isLetterOrDigit() }

    /**
     * La mejor letra de una búsqueda de LRCLIB para una canción: mismo artista (o que lo contenga),
     * duración parecida si se sabe, y mejor sincronizada que sin tiempos.
     */
    fun best(candidates: List<LyricsCandidate>, title: String, artist: String, durationSec: Int): LyricsCandidate? {
        val a = key(artist)
        val t = key(title)
        return candidates.asSequence()
            .filter { c -> c.instrumental || !c.synced.isNullOrBlank() || !c.plain.isNullOrBlank() }
            .filter { c ->
                val ca = key(c.artist)
                a.isEmpty() || ca.isEmpty() || ca.contains(a) || a.contains(ca)
            }
            .filter { c -> durationSec <= 0 || c.durationSec <= 0.0 || kotlin.math.abs(c.durationSec - durationSec) <= TOLERANCE_SEC }
            .sortedWith(
                compareByDescending<LyricsCandidate> { key(it.track) == t }
                    .thenByDescending { !it.synced.isNullOrBlank() }
                    .thenBy { if (durationSec <= 0) 0.0 else kotlin.math.abs(it.durationSec - durationSec) },
            )
            .firstOrNull()
    }
}
