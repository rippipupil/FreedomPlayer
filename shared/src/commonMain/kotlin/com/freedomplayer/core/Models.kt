package com.freedomplayer.core

/**
 * Una canción de la biblioteca. [score] va de 1 a 10 (medias estrellas) o es null si no tiene nota.
 * Las de YouTube tienen el id «yt:<id del vídeo>»; los archivos propios, «local:…».
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    /** Color de la carátula mientras no haya imagen (ARGB). */
    val coverColor: Long,
    val plays: Int = 0,
    val score: Int? = null,
    val album: String? = null,
    /** El audio descargado, en el dispositivo (null en los datos de ejemplo). */
    val audioPath: String? = null,
    /** La carátula: un archivo del dispositivo o una dirección web. */
    val cover: String? = null,
    /** De dónde se descargó («https://music.youtube.com/watch?v=…»); null si es un archivo propio. */
    val sourceUrl: String? = null,
    /** Cuándo entró en la biblioteca (milisegundos desde 1970). */
    val addedAt: Long = 0,
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String>,
    val coverColor: Long,
)

/** Ranking personal ordenado a mano («Top gym», «Para noches tristes»…). */
data class CustomRanking(
    val id: String,
    val name: String,
    val songIds: List<String>,
)

/** Pestañas del buscador: canciones de YouTube Music o vídeos de YouTube (directos, remixes, covers). */
enum class ResultKind { Cancion, Video }

/** Un resultado del buscador. El id es el de la canción que sería en la biblioteca («yt:…»). */
data class SearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val coverColor: Long,
    val kind: ResultKind,
    val album: String? = null,
    /** «1,2 M visualizaciones», «Canal de fans · 210 k»… (solo vídeos). */
    val note: String? = null,
    /** Dirección de YouTube o YouTube Music («https://…/watch?v=…»). */
    val url: String = "",
    /** Miniatura o carátula (dirección web). */
    val cover: String? = null,
) {
    fun toSong(): Song = Song(id, title, artist, durationSec, coverColor, album = album, cover = cover, sourceUrl = url.ifEmpty { null })

    /**
     * La canción tal y como entra en la biblioteca. Los vídeos llegan con el título de YouTube,
     * así que se limpia («Official Video», el artista delante…). Luego se podrá editar.
     */
    fun toLibrarySong(): Song = when (kind) {
        ResultKind.Cancion -> toSong()
        ResultKind.Video -> {
            val clean = TitleCleaner.clean(title, artist)
            Song(id, clean.title, clean.artist, durationSec, coverColor, cover = cover, sourceUrl = url.ifEmpty { null })
        }
    }
}

/** Una escucha: qué canción y cuándo (milisegundos desde 1970). Sirve para «Recientes» y «Más escuchadas». */
data class PlayEvent(val songId: String, val atMillis: Long)
