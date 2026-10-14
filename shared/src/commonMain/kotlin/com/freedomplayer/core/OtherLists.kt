package com.freedomplayer.core

/**
 * Una lista hecha por otra persona en YouTube Music o YouTube. No se importa entera: se abre y se
 * añaden las canciones que se quieran (o todas).
 */
data class PlaylistResult(
    val title: String,
    /** Quién la ha hecho («YouTube Music», el nombre del canal…). */
    val owner: String,
    /** Cuántas canciones dice que tiene; null si no se sabe. */
    val count: Int?,
    val coverColor: Long,
    /** Su dirección («https://music.youtube.com/playlist?list=…»). */
    val url: String,
    /** Carátula (dirección web). */
    val cover: String? = null,
    /** Sus canciones (ids del catálogo), solo en los datos de ejemplo. */
    val songIds: List<String> = emptyList(),
) {
    val key: String get() = url

    /** De YouTube Music (canciones) o de YouTube (vídeos, que al añadirlos se limpian). */
    val isMusic: Boolean get() = "music.youtube.com" in url
}

/** Una lista de otra persona ya abierta: sus datos y sus canciones, en su orden. */
data class OtherList(val info: PlaylistResult, val tracks: List<SearchResult>)

/** Enlaces de YouTube que se pegan en el buscador. */
object YouTubeLinks {
    private val host = Regex("""^(https?://)?([a-z0-9-]+\.)*(youtube\.com|youtu\.be)(/|$)""", RegexOption.IGNORE_CASE)
    private val listId = Regex("""[?&]list=([A-Za-z0-9_-]{2,})""")

    /**
     * Si [text] es el enlace de una lista de YouTube o YouTube Music («…/playlist?list=…»,
     * «…/watch?v=…&list=…», «youtu.be/…?list=…»), su dirección para abrirla; si no, null.
     * Las de YouTube Music se quedan en YouTube Music (sus canciones llegan como canciones).
     */
    fun playlistUrl(text: String): String? {
        val t = text.trim()
        if (t.any { it.isWhitespace() } || !host.containsMatchIn(t)) return null
        val id = listId.find(t)?.groupValues?.get(1) ?: return null
        // Las mezclas automáticas («RD…») dependen del vídeo del que salen: se abren tal cual.
        if (id.startsWith("RD")) return if (t.startsWith("http", ignoreCase = true)) t else "https://$t"
        val music = t.contains("music.youtube.com", ignoreCase = true)
        return if (music) "https://music.youtube.com/playlist?list=$id" else "https://www.youtube.com/playlist?list=$id"
    }
}
