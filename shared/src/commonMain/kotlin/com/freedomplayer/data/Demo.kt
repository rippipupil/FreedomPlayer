package com.freedomplayer.data

import com.freedomplayer.core.AlbumResult
import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.Search
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.Song
import com.freedomplayer.core.SpotifyPlaylist
import com.freedomplayer.core.Stats
import kotlinx.coroutines.delay

/** Datos de ejemplo de los tableros del diseño: para las pruebas de la interfaz y las capturas. */
fun Services.Companion.demo(startMillis: Long): Services = Services(
    store = MemoryStore(
        LibrarySnapshot(SampleData.songs, SampleData.playlists, SampleData.customRankings, SampleData.playLog(startMillis)),
    ),
    source = SampleSource(),
    downloader = SimulatedDownloader(),
    lyrics = SampleLyrics(),
    engine = SimulatedEngine(),
    demo = true,
    spotify = SampleSpotify(),
)

/** Biblioteca en memoria (no se guarda en ningún sitio). */
class MemoryStore(seed: LibrarySnapshot = LibrarySnapshot(emptyList(), emptyList(), emptyList(), emptyList())) : LibraryStore {
    private val songs = LinkedHashMap<String, Song>().apply { seed.songs.forEach { put(it.id, it) } }
    private val playlists = LinkedHashMap<String, Pair<Playlist, Int>>().apply { seed.playlists.forEachIndexed { i, p -> put(p.id, p to i) } }
    private val rankings = LinkedHashMap<String, Pair<CustomRanking, Int>>().apply { seed.rankings.forEachIndexed { i, r -> put(r.id, r to i) } }
    private val log = seed.playLog.toMutableList()
    private val lyrics = HashMap<String, Pair<LyricsData?, Long>>()
    private val settings = HashMap<String, String>()

    override fun load(): LibrarySnapshot {
        val counts = log.groupingBy { it.songId }.eachCount()
        return LibrarySnapshot(
            // Las de ejemplo traen sus escuchas de siempre; las nuevas, las del historial.
            songs.values.map { s -> if (s.plays == 0) s.copy(plays = counts[s.id] ?: 0) else s },
            playlists.values.sortedBy { it.second }.map { it.first },
            rankings.values.sortedBy { it.second }.map { it.first },
            log.toList(),
        )
    }

    override fun saveSong(song: Song) {
        songs[song.id] = song
    }

    override fun deleteSong(id: String) {
        songs.remove(id)
        log.removeAll { it.songId == id }
    }

    override fun setScore(songId: String, score: Int?) {
        songs[songId]?.let { songs[songId] = it.copy(score = score) }
    }

    override fun savePlaylist(playlist: Playlist, position: Int) {
        playlists[playlist.id] = playlist to position
    }

    override fun deletePlaylist(id: String) {
        playlists.remove(id)
    }

    override fun saveRanking(ranking: CustomRanking, position: Int) {
        rankings[ranking.id] = ranking to position
    }

    override fun deleteRanking(id: String) {
        rankings.remove(id)
    }

    override fun addPlay(event: PlayEvent) {
        log += event
    }

    override fun lyrics(songId: String): LyricsData? = lyrics[songId]?.first

    override fun saveLyrics(songId: String, lyrics: LyricsData?, checkedAt: Long) {
        this.lyrics[songId] = lyrics to checkedAt
    }

    override fun lyricsCheckedAt(songId: String): Long? = lyrics[songId]?.second

    override fun setting(key: String): String? = settings[key]

    override fun setSetting(key: String, value: String?) {
        if (value == null) settings.remove(key) else settings[key] = value
    }

    override fun recentlyPlayed(limit: Int): List<String> =
        Stats.recentlyPlayed(songs.values.toList(), log, limit).map { it.id }

    override fun mostPlayedSince(sinceMillis: Long, limit: Int): List<Pair<String, Int>> {
        val counts = Stats.playsSince(log, sinceMillis)
        return songs.values.mapNotNull { s -> counts[s.id]?.let { s to it } }
            .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })
            .take(limit)
            .map { it.first.id to it.second }
    }

    override fun unratedPlayed(): List<Pair<String, Int>> {
        val counts = log.groupingBy { it.songId }.eachCount()
        return songs.values.filter { it.score == null && (counts[it.id] ?: 0) > 0 }
            .sortedWith(compareByDescending<Song> { counts[it.id] ?: 0 }.thenBy { it.title.lowercase() })
            .map { it.id to (counts[it.id] ?: 0) }
    }
}

/** El buscador de ejemplo: busca en el catálogo inventado de [SampleData]. */
class SampleSource(private val latencyMs: Long = 150) : MusicSource {
    override suspend fun searchSongs(query: String): List<SearchResult> {
        delay(latencyMs)
        return Search.results(SampleData.catalog, query, ResultKind.Cancion)
    }

    override suspend fun searchVideos(query: String): List<SearchResult> {
        delay(latencyMs)
        return Search.results(SampleData.catalog, query, ResultKind.Video)
    }

    override suspend fun searchAlbums(query: String): List<AlbumResult> {
        delay(latencyMs)
        return Search.albums(SampleData.albums, SampleData.catalog, query)
    }

    override suspend fun albumTracks(album: AlbumResult): List<SearchResult> {
        delay(latencyMs)
        return album.songIds.mapNotNull { id -> SampleData.catalog.firstOrNull { it.id == id } }
    }

    override suspend fun stream(result: SearchResult): StreamSource {
        delay(latencyMs)
        return StreamSource("demo:${result.id}", durationSec = result.durationSec)
    }
}

/** La playlist de Spotify de ejemplo («Noches en el coche»), sin salir a internet. */
class SampleSpotify(private val latencyMs: Long = 400) : SpotifyReader {
    override suspend fun playlist(id: String): SpotifyPlaylist {
        delay(latencyMs)
        return SpotifyPlaylist(SampleData.SPOTIFY_PLAYLIST_NAME, "tú", SampleData.spotifyTracks)
    }
}

/**
 * Descarga simulada: ~1,3 s por canción. El cover a piano («v3») falla la primera vez, para ver
 * el aviso de error con «REINTENTAR».
 */
class SimulatedDownloader : MediaDownloader {
    private val failedOnce = mutableSetOf<String>()

    override suspend fun download(request: DownloadRequest, progress: (Float) -> Unit): DownloadedSong {
        for (step in 1..12) {
            delay(110)
            progress(step / 12f)
        }
        if (request.result.id == "v3" && failedOnce.add(request.result.id)) {
            throw SourceException(FailureKind.Changed, "Simulado")
        }
        val song = request.result.toLibrarySong().copy(title = request.title, artist = request.artist, album = request.album ?: request.result.album)
        return DownloadedSong(song, SampleData.lyricsData(song.id))
    }
}

class SampleLyrics : LyricsSource {
    override suspend fun find(title: String, artist: String, album: String?, durationSec: Int): LyricsData? = null
}

/**
 * Audio simulado (pruebas y capturas): el tiempo avanza con [tick] y la cola se comporta como la
 * de verdad (siguiente, repetir, fin de la cola), pero no suena nada.
 */
class SimulatedEngine : AudioEngine {
    private var tracks: List<Track> = emptyList()
    private var index = -1
    private var playing = false
    private var position = 0L
    private var repeat = RepeatMode.Off
    private var transitions = 0
    private var ended = false
    private var pauseAtEnd = false
    private var pausedAtEnd = false

    private class Preview(val durationMs: Long) {
        var position = 0L
        var playing = true
        var ended = false
    }

    private var preview: Preview? = null

    private val duration: Long get() = tracks.getOrNull(index)?.durationMs ?: 0L

    override fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean) {
        this.tracks = tracks
        this.index = if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)
        position = positionMs.coerceAtLeast(0)
        playing = play && this.index >= 0
        ended = false
        pausedAtEnd = false
        transitions++
    }

    override fun reorder(tracks: List<Track>, index: Int) {
        this.tracks = tracks
        this.index = if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)
    }

    override fun play() {
        if (index < 0) return
        if (ended) {
            position = 0
            ended = false
        }
        playing = true
        if (pausedAtEnd) {
            pausedAtEnd = false
            moveOn()
        }
    }

    override fun pause() {
        playing = false
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs.coerceIn(0, maxOf(0L, duration))
        ended = false
        pausedAtEnd = false
    }

    override fun skipTo(index: Int) {
        if (index !in tracks.indices) return
        this.index = index
        position = 0
        ended = false
        pausedAtEnd = false
        transitions++
    }

    override fun setPauseAtEndOfSong(on: Boolean) {
        pauseAtEnd = on
    }

    override fun setRepeat(mode: RepeatMode) {
        repeat = mode
    }

    override fun setVolume(volume: Float) = Unit

    override fun startPreview(source: StreamSource) {
        preview = Preview(source.durationSec * 1000L)
    }

    override fun previewPlay() {
        preview?.let { if (!it.ended) it.playing = true }
    }

    override fun previewPause() {
        preview?.playing = false
    }

    override fun previewSeek(positionMs: Long) {
        preview?.let { it.position = positionMs.coerceIn(0, it.durationMs) }
    }

    override fun stopPreview() {
        preview = null
    }

    override fun snapshot(): EngineSnapshot = EngineSnapshot(
        index = index,
        playing = playing,
        positionMs = position,
        durationMs = duration,
        ended = ended,
        transitions = transitions,
        preview = preview?.let { PreviewSnapshot(it.playing, false, it.position, it.durationMs, it.ended, null) },
    )

    override fun tick(elapsedMs: Long) {
        val p = preview
        if (p != null) {
            if (p.playing) {
                p.position += elapsedMs
                if (p.position >= p.durationMs) {
                    p.position = p.durationMs
                    p.playing = false
                    p.ended = true
                }
            }
            return
        }
        if (!playing || index < 0) return
        position += elapsedMs
        if (position < duration) return
        if (pauseAtEnd) {
            // Temporizador: se queda al final de esta, en pausa.
            position = duration
            playing = false
            pausedAtEnd = true
            return
        }
        moveOn()
    }

    /** Ha acabado la que sonaba: la siguiente según «repetir», o fin de la cola. */
    private fun moveOn() {
        position = 0
        when {
            repeat == RepeatMode.One -> transitions++
            index < tracks.lastIndex -> {
                index++
                transitions++
            }
            repeat == RepeatMode.All -> {
                index = 0
                transitions++
            }
            else -> {
                playing = false
                ended = true
            }
        }
    }
}
