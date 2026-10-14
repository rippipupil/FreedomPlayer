package com.freedomplayer.data

import com.freedomplayer.core.AlbumResult
import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.Song

/**
 * Lo que la app necesita de fuera: biblioteca guardada, buscador, descargas, letras y audio.
 * Cada plataforma pone los de verdad; [demo] usa datos de ejemplo (pruebas y capturas).
 */
class Services(
    val store: LibraryStore,
    val source: MusicSource,
    val downloader: MediaDownloader,
    val lyrics: LyricsSource,
    val engine: AudioEngine,
    /** Datos de ejemplo: biblioteca, buscador y Spotify inventados, audio simulado. */
    val demo: Boolean = false,
) {
    companion object
}

/** Por qué ha fallado algo de YouTube, para decirlo claro en el aviso. */
enum class FailureKind { Network, Blocked, Unavailable, Changed, Storage, Unknown }

class SourceException(val kind: FailureKind, message: String, cause: Throwable? = null) : Exception(message, cause)

/** Texto de los avisos de error según lo que haya pasado. */
fun FailureKind.explain(): String = when (this) {
    FailureKind.Network -> "No hay conexión con YouTube. Comprueba la red y reintenta."
    FailureKind.Blocked -> "YouTube pide comprobar que no eres un robot. Prueba otra vez dentro de un rato."
    FailureKind.Unavailable -> "Este vídeo no se puede descargar: tiene restricción de edad, de país o es de pago."
    FailureKind.Changed -> "YouTube ha cambiado algo. La app se actualizará sola; puedes reintentar."
    FailureKind.Storage -> "No queda espacio o no se puede escribir en el almacenamiento."
    FailureKind.Unknown -> "Algo ha fallado. Puedes reintentar."
}

fun Throwable.failureKind(): FailureKind = (this as? SourceException)?.kind ?: FailureKind.Unknown

// ---------- Buscador ----------

/** Dirección para escuchar algo ya, sin descargarlo (vista previa). */
data class StreamSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val durationSec: Int = 0,
)

/** YouTube Music (canciones y álbumes) y YouTube (vídeos). */
interface MusicSource {
    suspend fun searchSongs(query: String): List<SearchResult>
    suspend fun searchVideos(query: String): List<SearchResult>
    suspend fun searchAlbums(query: String): List<AlbumResult>

    /** Las canciones de un álbum, en orden. */
    suspend fun albumTracks(album: AlbumResult): List<SearchResult>

    /** El audio de un resultado para escucharlo ya (vista previa). */
    suspend fun stream(result: SearchResult): StreamSource
}

// ---------- Descargas y letras ----------

/** Qué descargar y con qué nombre (los vídeos llegan con el título ya limpio o corregido). */
data class DownloadRequest(
    val result: SearchResult,
    val title: String,
    val artist: String,
    val album: String? = null,
)

data class DownloadedSong(val song: Song, val lyrics: LyricsData?)

/** Descarga el mejor audio, la carátula y la letra sincronizada. */
interface MediaDownloader {
    /** [progress] va de 0 a 1. Lanza [SourceException] si no se puede. */
    suspend fun download(request: DownloadRequest, progress: (Float) -> Unit): DownloadedSong

    /** Borra los archivos de una canción (al quitarla de la biblioteca). */
    fun deleteFiles(song: Song) {}
}

/** Letras de LRCLIB. */
interface LyricsSource {
    /** null si no hay letra (o no se ha podido preguntar). */
    suspend fun find(title: String, artist: String, album: String?, durationSec: Int): LyricsData?
}

// ---------- Biblioteca guardada ----------

data class LibrarySnapshot(
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val rankings: List<CustomRanking>,
    val playLog: List<PlayEvent>,
)

/** La biblioteca en disco. Cada cambio se guarda en cuanto pasa. */
interface LibraryStore : HistoryQueries {
    fun load(): LibrarySnapshot
    fun saveSong(song: Song)
    fun deleteSong(id: String)
    fun setScore(songId: String, score: Int?)

    /** Guarda la playlist entera (nombre, color y canciones en orden). */
    fun savePlaylist(playlist: Playlist, position: Int)
    fun deletePlaylist(id: String)
    fun saveRanking(ranking: CustomRanking, position: Int)
    fun deleteRanking(id: String)
    fun addPlay(event: PlayEvent)

    fun lyrics(songId: String): LyricsData?

    /** Guarda la letra (o que no la hay, con [lyrics] null) y cuándo se buscó. */
    fun saveLyrics(songId: String, lyrics: LyricsData?, checkedAt: Long)

    /** Cuándo se buscó la letra por última vez, o null si nunca. */
    fun lyricsCheckedAt(songId: String): Long?

    fun setting(key: String): String?
    fun setSetting(key: String, value: String?)
}

/** Consultas del historial de escuchas (Inicio y Ranking). */
interface HistoryQueries {
    /** Ids de lo último que ha sonado, sin repetir, de más reciente a menos. */
    fun recentlyPlayed(limit: Int): List<String>

    /** Ids y escuchas desde [sinceMillis], de más a menos (empates por título). */
    fun mostPlayedSince(sinceMillis: Long, limit: Int): List<Pair<String, Int>>

    /** Escuchadas alguna vez y sin nota, con sus escuchas, de más a menos. */
    fun unratedPlayed(): List<Pair<String, Int>>
}

// ---------- Audio ----------

/** Una canción de la cola tal y como la necesita el reproductor. */
data class Track(
    val id: String,
    /** Archivo local o dirección. */
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUri: String?,
    val durationMs: Long,
)

data class PreviewSnapshot(
    val playing: Boolean,
    val loading: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
    val error: String?,
)

/** Cómo va el audio ahora mismo. La app lo consulta varias veces por segundo. */
data class EngineSnapshot(
    /** Posición en la cola que suena (-1 si no hay cola). */
    val index: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** La cola ha terminado (sin repetir). */
    val ended: Boolean = false,
    /** Sube cada vez que empieza una canción (también al repetir la misma). */
    val transitions: Int = 0,
    /** Último error y un contador para saber si es nuevo. */
    val error: String? = null,
    val errorSerial: Int = 0,
    val preview: PreviewSnapshot? = null,
)

/**
 * El reproductor de verdad (Media3 en Android, FFmpeg en el PC). La cola la decide la app
 * ([com.freedomplayer.core.PlayQueue], con su aleatorio) y aquí se toca en ese orden.
 */
interface AudioEngine {
    /** Cola nueva: [tracks] en orden; empieza en [index] desde [positionMs]. */
    fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean)

    /**
     * La cola cambia (aleatorio, reordenar o quitar canciones) pero la que suena, ahora en
     * [index], es la misma y sigue sin cortes.
     */
    fun reorder(tracks: List<Track>, index: Int)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipTo(index: Int)
    fun setRepeat(mode: RepeatMode)

    /** 0–1, el volumen de la app (en el móvil manda el del sistema). */
    fun setVolume(volume: Float)

    fun startPreview(source: StreamSource)
    fun previewPlay()
    fun previewPause()
    fun previewSeek(positionMs: Long)
    fun stopPreview()

    fun snapshot(): EngineSnapshot

    /**
     * Temporizador «al acabar esta canción»: en vez de pasar a la siguiente, se queda en pausa al
     * final de la que suena (con play sigue con la siguiente).
     */
    fun setPauseAtEndOfSong(on: Boolean) {}

    /** Para el audio simulado: el tiempo que ha pasado. Los de verdad no lo necesitan. */
    fun tick(elapsedMs: Long) {}

    fun release() {}
}
