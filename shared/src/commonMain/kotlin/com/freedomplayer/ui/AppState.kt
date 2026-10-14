package com.freedomplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.freedomplayer.core.AlbumResult
import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.MatchKind
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.PlayQueue
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Ranking
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.Search
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.Song
import com.freedomplayer.core.Spotify
import com.freedomplayer.core.SpotifyLink
import com.freedomplayer.core.TrackMatch
import com.freedomplayer.core.starsText
import com.freedomplayer.ui.components.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** Pantallas. Las cinco primeras son las del menú de los paneles. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Rank : Screen
    data object Stats : Screen
    data object Settings : Screen
    data class PlaylistDetail(val id: String) : Screen
    data object SpotifyImport : Screen

    val inMenu: Boolean get() = this == Home || this == Search || this == Rank || this == Stats || this == Settings
}

enum class Side { Left, Right }

enum class SearchTab { Songs, Albums, Videos }

enum class RankTab { General, Mine, Unrated }

/** Color de la raya de un aviso. */
enum class Tone { Pink, Jade, Neon, Bone }

/** Lo que se quiere añadir al tocar «+». */
sealed interface AddTarget {
    data class Result(val result: SearchResult) : AddTarget
    data class Album(val album: AlbumResult) : AddTarget
    /** Una canción que ya está en la biblioteca (desde el reproductor): solo se elige playlist. */
    data class LibrarySong(val songId: String) : AddTarget
}

sealed interface Sheet {
    /** El selector de «+»: una playlist o solo la biblioteca. */
    data class Add(val target: AddTarget) : Sheet

    /** «⋯» del reproductor. */
    data object More : Sheet
}

sealed interface Prompt {
    /** Crear una playlist y, si hace falta, añadir después lo que se estaba añadiendo. */
    data class NewPlaylist(val then: AddTarget? = null, val title: String? = null, val artist: String? = null) : Prompt
    data object NewRanking : Prompt
}

/** Aviso positivo: sale abajo y se va solo. Puede llevar una acción («DESHACER»). */
data class Toast(
    val id: Long,
    val text: String,
    val mood: Mood,
    val tone: Tone,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val millis: Long = 2600,
)

/** Error: se queda hasta que se toca, con «REINTENTAR» si se puede. */
data class ErrorNotice(val id: Long, val title: String, val body: String, val retry: (() -> Unit)?)

/** Vista previa de un resultado del buscador (canción o vídeo): suena entera. */
data class Preview(val id: String, val title: String, val artist: String, val durationSec: Int, val coverColor: Long)

/** Una descarga: una canción, un álbum o lo que falta de una playlist importada. */
class DownloadJob(
    val id: Long,
    val title: String,
    val songs: List<Song>,
    val playlistId: String?,
    val isCollection: Boolean,
) {
    var done by mutableIntStateOf(0)
    var itemProgress by mutableFloatStateOf(0f)
    val total: Int get() = songs.size
    val fraction: Float get() = if (total == 0) 1f else ((done + itemProgress) / total).coerceIn(0f, 1f)
}

enum class ImportPhase { Paste, Matching, Done }

/** «Importar de Spotify» (y «Pegar playlist de Spotify», que es lo mismo). */
class SpotifyImportState {
    var link by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var phase by mutableStateOf(ImportPhase.Paste)
    var checked by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    val matches = mutableStateListOf<TrackMatch>()

    /** Coincidencias dudosas ya decididas: true = añadida, false = descartada. */
    val decisions = mutableStateMapOf<Int, Boolean>()
    var playlistId by mutableStateOf<String?>(null)

    fun reset() {
        link = ""
        error = null
        phase = ImportPhase.Paste
        checked = 0
        total = 0
        matches.clear()
        decisions.clear()
        playlistId = null
    }
}

class Settings {
    var normalizeVolume by mutableStateOf(true)
    var crossfade by mutableStateOf(false)
    var syncedLyrics by mutableStateOf(true)
    var keepOffline by mutableStateOf(true)
    var closeToTray by mutableStateOf(true)
    var miniOnTop by mutableStateOf(true)

    /** Para probar los estados sin red y con la biblioteca vacía. */
    var demoOffline by mutableStateOf(false)
    var demoEmpty by mutableStateOf(false)
}

/**
 * Todo el estado de la app (hito 1: datos de ejemplo y reproducción simulada, sin audio).
 * Lo comparten el móvil y el PC. Los temporizadores corren mientras [run] está activo.
 */
class AppState(startMillis: Long = nowMillis()) {

    // ---------- Biblioteca ----------

    val songs = mutableStateListOf<Song>().apply { addAll(SampleData.songs) }
    val playlists = mutableStateListOf<Playlist>().apply { addAll(SampleData.playlists) }
    val rankings = mutableStateListOf<CustomRanking>().apply { addAll(SampleData.customRankings) }
    val playLog = mutableStateListOf<PlayEvent>().apply { addAll(SampleData.playLog(startMillis)) }
    val settings = Settings()

    /** La biblioteca que se ve (vacía si se está probando «Biblioteca vacía»). */
    val library: List<Song> get() = if (settings.demoEmpty) emptyList() else songs

    fun song(id: String?): Song? = if (id == null) null else songs.firstOrNull { it.id == id }

    fun playlist(id: String?): Playlist? = if (id == null) null else playlists.firstOrNull { it.id == id }

    // ---------- Plataforma ----------

    /** Lo pone la plataforma (Android: ConnectivityManager; PC: interfaces de red). */
    var systemOnline by mutableStateOf(true)

    /** Lo pone la plataforma: el sistema pide reducir el movimiento. */
    var reduceMotion by mutableStateOf(false)

    val online: Boolean get() = systemOnline && !settings.demoOffline

    // ---------- Navegación ----------

    val history = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = history.lastOrNull() ?: Screen.Home

    /** Hacia dónde va la última navegación (para la dirección de la transición). */
    var navForward by mutableStateOf(true)
        private set
    var panel by mutableStateOf<Side?>(null)
    var playerOpen by mutableStateOf(false)
    var sheet by mutableStateOf<Sheet?>(null)
    var prompt by mutableStateOf<Prompt?>(null)

    val canGoBack: Boolean
        get() = prompt != null || sheet != null || panel != null || playerOpen || history.size > 1

    fun go(target: Screen) {
        panel = null
        sheet = null
        playerOpen = false
        val from = screen
        if (target == from) return
        navForward = target != Screen.Home
        if (target.inMenu) {
            history.clear()
            history.add(Screen.Home)
            if (target != Screen.Home) history.add(target)
        } else {
            history.add(target)
        }
        leaving(from)
    }

    /** Atrás: cierra lo que esté abierto, de lo más reciente a lo más antiguo. false si no hay nada. */
    fun back(): Boolean {
        when {
            prompt != null -> prompt = null
            sheet != null -> sheet = null
            panel != null -> panel = null
            playerOpen -> playerOpen = false
            history.size > 1 -> {
                val from = history.removeAt(history.lastIndex)
                navForward = false
                leaving(from)
            }
            else -> return false
        }
        return true
    }

    fun openPanel(side: Side) {
        panel = side
    }

    fun closePanel() {
        panel = null
    }

    private fun leaving(from: Screen) {
        if (from == Screen.Rank && screen != Screen.Rank) {
            freshIds.clear()
            editingScoreId = null
        }
    }

    // ---------- Reproducción (simulada) ----------

    var queue by mutableStateOf(PlayQueue.of(SampleData.playlists[0].songIds, "s5"))
        private set

    /** Qué está sonando («NOCHES DE NEÓN», «TU BIBLIOTECA»…), para «SONANDO · …». */
    var queueSource by mutableStateOf("NOCHES DE NEÓN")
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(84_000L)
        private set

    /** Sube con cada play, pausa o cambio de canción: los fantasmas dan un saltito. */
    var reaction by mutableIntStateOf(0)
        private set
    var volume by mutableFloatStateOf(0.75f)
    var muted by mutableStateOf(false)
    private var counted = false

    val currentSong: Song? get() = if (settings.demoEmpty) null else song(queue.current)

    /** Toca una canción de una lista: suena y la cola sigue con el resto de esa lista. */
    fun playFrom(ids: List<String>, startId: String, source: String) {
        val playable = ids.filter { song(it) != null }
        if (playable.isEmpty()) return
        dropPreview()
        queue = PlayQueue.of(playable, startId, shuffle = queue.shuffle, repeat = queue.repeat)
        queueSource = source
        positionMs = 0
        counted = false
        isPlaying = true
        reaction++
    }

    /** REPRODUCIR / ALEATORIO de una playlist. */
    fun playPlaylist(id: String, shuffle: Boolean) {
        val p = playlist(id) ?: return
        val ids = p.songIds.filter { song(it) != null }
        if (ids.isEmpty()) return
        dropPreview()
        queue = PlayQueue.of(ids, if (shuffle) ids.random() else ids.first(), shuffle = shuffle, repeat = queue.repeat)
        queueSource = p.name.uppercase()
        positionMs = 0
        counted = false
        isPlaying = true
        reaction++
    }

    fun togglePlay() {
        if (preview != null) {
            previewPlaying = !previewPlaying
            reaction++
            return
        }
        if (currentSong == null) return
        isPlaying = !isPlaying
        reaction++
    }

    fun next() {
        if (preview != null) {
            stopPreview()
            return
        }
        val n = queue.next()
        if (n == null) {
            isPlaying = false
            positionMs = 0
        } else {
            queue = n
            positionMs = 0
        }
        counted = false
        reaction++
    }

    fun previous() {
        if (preview != null) {
            previewPositionMs = 0
            return
        }
        if (positionMs > 3_000) {
            positionMs = 0
        } else {
            queue = queue.previous()
            positionMs = 0
            counted = false
        }
        reaction++
    }

    fun seekTo(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        val p = preview
        if (p != null) {
            previewPositionMs = (p.durationSec * 1000L * f).toLong()
            return
        }
        val s = currentSong ?: return
        positionMs = (s.durationSec * 1000L * f).toLong()
    }

    fun toggleShuffle() {
        queue = queue.toggleShuffle()
    }

    fun cycleRepeat() {
        queue = queue.cycleRepeat()
    }

    private fun advance(dt: Long) {
        val p = preview
        if (p != null) {
            if (previewPlaying) {
                previewPositionMs += dt
                if (previewPositionMs >= p.durationSec * 1000L) stopPreview()
            }
            return
        }
        val s = currentSong ?: return
        if (!isPlaying) return
        positionMs += dt
        // Cuenta como escuchada a los 30 s (o a la mitad si es más corta).
        if (!counted && positionMs >= minOf(30_000L, s.durationSec * 500L)) {
            counted = true
            playLog.add(PlayEvent(s.id, nowMillis()))
            replaceSong(s.id) { it.copy(plays = it.plays + 1) }
        }
        if (positionMs >= s.durationSec * 1000L) {
            val n = queue.next(auto = true)
            if (n == null) {
                isPlaying = false
            } else {
                queue = n
            }
            positionMs = 0
            counted = false
            reaction++
        }
    }

    // ---------- Vista previa ----------

    var preview by mutableStateOf<Preview?>(null)
        private set
    var previewPlaying by mutableStateOf(false)
        private set
    var previewPositionMs by mutableLongStateOf(0L)
        private set
    private var resumeAfterPreview = false

    /**
     * Escuchar antes de añadir: suena la canción entera y se pausa lo que sonaba.
     * Al cerrar la vista previa, vuelve lo que sonaba.
     */
    fun togglePreview(r: SearchResult) {
        if (preview?.id == r.id) {
            stopPreview()
            return
        }
        if (!online) return
        if (preview == null) {
            resumeAfterPreview = isPlaying
            isPlaying = false
        }
        preview = Preview(r.id, r.title, r.artist, r.durationSec, r.coverColor)
        previewPositionMs = 0
        previewPlaying = true
        reaction++
    }

    fun stopPreview() {
        if (preview == null) return
        preview = null
        previewPlaying = false
        if (resumeAfterPreview && currentSong != null) isPlaying = true
        resumeAfterPreview = false
        reaction++
    }

    private fun dropPreview() {
        preview = null
        previewPlaying = false
        resumeAfterPreview = false
    }

    // ---------- Buscar y añadir ----------

    var query by mutableStateOf("luz de farola")
    var searchTab by mutableStateOf(SearchTab.Songs)

    /** Resultados añadidos en esta sesión (su «+» pasa a ✓). */
    val addedIds = mutableStateListOf<String>()
    val downloads = mutableStateListOf<DownloadJob>()
    private val failedOnce = mutableSetOf<String>()
    private var worker: Job? = null

    /** La canción de la biblioteca que corresponde a un resultado, si ya la tienes. */
    fun librarySongFor(r: SearchResult): Song? =
        songs.firstOrNull { it.id == r.id }
            ?: songs.firstOrNull {
                Search.normalize(it.title) == Search.normalize(r.title) && Search.normalize(it.artist) == Search.normalize(r.artist)
            }

    /** Cómo entra un resultado en la biblioteca: los vídeos con el título limpio (o el que se haya escrito). */
    fun songFor(r: SearchResult, title: String? = null, artist: String? = null): Song {
        val base = r.toLibrarySong()
        return base.copy(
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: base.title,
            artist = artist?.trim()?.takeIf { it.isNotEmpty() } ?: base.artist,
        )
    }

    /** Progreso de descarga de un resultado (0–1), o null si no se está descargando. */
    fun downloadFraction(resultId: String): Float? {
        val first = downloads.firstOrNull()
        for (job in downloads) {
            val i = job.songs.indexOfFirst { it.id == resultId }
            if (i >= 0 && i >= job.done) return if (job == first && i == job.done) job.itemProgress else 0f
        }
        return null
    }

    fun albumJob(album: AlbumResult): DownloadJob? = downloads.firstOrNull { it.isCollection && it.title == album.title }

    /** «+» siempre abre el selector: una playlist o solo la biblioteca. */
    fun requestAdd(target: AddTarget) {
        sheet = Sheet.Add(target)
    }

    /** Lo elegido en el selector. [playlistId] null = solo a la biblioteca. */
    fun confirmAdd(target: AddTarget, playlistId: String?, title: String? = null, artist: String? = null) {
        sheet = null
        when (target) {
            is AddTarget.LibrarySong -> {
                val p = playlist(playlistId) ?: return
                addToPlaylist(p.id, listOf(target.songId))
                toast("Añadida a «${p.name}».", Mood.Feliz)
            }
            is AddTarget.Result -> {
                val r = target.result
                val have = librarySongFor(r)
                if (have != null) {
                    val p = playlist(playlistId)
                    if (p != null) {
                        addToPlaylist(p.id, listOf(have.id))
                        toast("Añadida a «${p.name}».", Mood.Feliz)
                    } else {
                        toast("Ya está en tu biblioteca.", Mood.Uwu, Tone.Jade)
                    }
                    return
                }
                val song = songFor(r, title, artist)
                if (r.id !in addedIds) addedIds.add(r.id)
                enqueue(DownloadJob(newId(), song.title, listOf(song), playlistId, isCollection = false))
            }
            is AddTarget.Album -> {
                val album = target.album
                val results = album.songIds.mapNotNull { id -> SampleData.catalog.firstOrNull { it.id == id } }
                val have = results.mapNotNull { librarySongFor(it) }
                val missing = results.filter { librarySongFor(it) == null && downloadFraction(it.id) == null }
                if (playlistId != null && have.isNotEmpty()) addToPlaylist(playlistId, have.map { it.id })
                if (missing.isEmpty()) {
                    toast("Ya tienes todo «${album.title}».", Mood.Uwu, Tone.Jade)
                    return
                }
                missing.forEach { if (it.id !in addedIds) addedIds.add(it.id) }
                enqueue(DownloadJob(newId(), album.title, missing.map { songFor(it) }, playlistId, isCollection = true))
            }
        }
    }

    private fun enqueue(job: DownloadJob) {
        downloads.add(job)
        if (worker?.isActive != true) worker = scope?.launch { work() }
    }

    private suspend fun work() {
        while (downloads.isNotEmpty()) {
            val job = downloads.first()
            val ok = runJob(job)
            downloads.remove(job)
            if (ok) finished(job)
        }
    }

    /** Descarga simulada: ~1,3 s por canción; se para mientras no hay conexión. */
    private suspend fun runJob(job: DownloadJob): Boolean {
        while (job.done < job.total) {
            val song = job.songs[job.done]
            var step = 0
            while (step < 12) {
                delay(110)
                if (!online) continue
                step++
                job.itemProgress = step / 12f
            }
            // El cover a piano falla la primera vez, para ver el aviso de error con «REINTENTAR».
            if (song.id == "v3" && failedOnce.add(song.id)) {
                job.itemProgress = 0f
                val rest = job.songs.drop(job.done)
                rest.forEach { addedIds.remove(it.id) }
                showError(
                    title = "No se pudo descargar",
                    body = "YouTube ha cambiado algo. La app se actualizará sola; puedes reintentar.",
                ) {
                    rest.forEach { if (it.id !in addedIds) addedIds.add(it.id) }
                    enqueue(DownloadJob(newId(), job.title, rest, job.playlistId, job.isCollection))
                }
                return false
            }
            if (songs.none { it.id == song.id }) songs.add(song)
            job.playlistId?.let { addToPlaylist(it, listOf(song.id)) }
            job.itemProgress = 0f
            job.done++
        }
        return true
    }

    private fun finished(job: DownloadJob) {
        val p = playlist(job.playlistId)
        val text = when {
            !job.isCollection && p != null -> "«${job.title}» ya está en tu biblioteca y en «${p.name}», con carátula y letra."
            !job.isCollection -> "«${job.title}» ya está en tu biblioteca, con carátula y letra."
            else -> "«${job.title}»: ${job.total} canciones nuevas en tu biblioteca."
        }
        toast(text, Mood.Owo)
    }

    // ---------- Playlists ----------

    fun addToPlaylist(id: String, songIds: List<String>) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = p.copy(songIds = p.songIds + songIds.filter { it !in p.songIds }.distinct())
    }

    fun createPlaylist(name: String): String {
        val id = "p${newId()}"
        var unique = name
        var n = 2
        while (playlists.any { it.name.equals(unique, ignoreCase = true) }) unique = "$name ($n)".also { n++ }
        val colors = com.freedomplayer.ui.theme.Fp.Covers
        playlists.add(Playlist(id, unique, emptyList(), colors[playlists.size % colors.size]))
        return id
    }

    fun submitPrompt(text: String) {
        val name = text.trim()
        if (name.isEmpty()) return
        when (val p = prompt) {
            is Prompt.NewPlaylist -> {
                prompt = null
                val id = createPlaylist(name)
                val then = p.then
                if (then != null) confirmAdd(then, id, p.title, p.artist) else toast("Playlist «$name» creada.", Mood.Feliz)
            }
            Prompt.NewRanking -> {
                prompt = null
                val id = "r${newId()}"
                rankings.add(CustomRanking(id, name, emptyList()))
                openRankingId = id
                toast("Ranking «$name» creado.", Mood.Feliz)
            }
            null -> Unit
        }
    }

    // ---------- Ranking ----------

    var rankTab by mutableStateOf(RankTab.General)

    /** Puntuadas desde que se entró en Ranking (fila en jade). Se limpia al salir. */
    val freshIds = mutableStateListOf<String>()
    var editingScoreId by mutableStateOf<String?>(null)
    var openRankingId by mutableStateOf<String?>("r1")

    /** Pone o cambia una nota. Sale un aviso con «DESHACER» unos segundos. */
    fun rate(songId: String, score: Int) {
        val i = songs.indexOfFirst { it.id == songId }
        if (i < 0) return
        val before = songs[i].score
        editingScoreId = null
        if (before == score) return
        songs[i] = songs[i].copy(score = score)
        if (songId !in freshIds) freshIds.add(songId)
        val title = songs[i].title
        toast("«$title»: ${starsText(score)}", Mood.Feliz, Tone.Jade, actionLabel = "DESHACER") {
            replaceSong(songId) { it.copy(score = before) }
            if (before == null) freshIds.remove(songId)
        }
    }

    fun moveInRanking(rankingId: String, from: Int, to: Int) {
        val i = rankings.indexOfFirst { it.id == rankingId }
        if (i >= 0) rankings[i] = Ranking.move(rankings[i], from, to)
    }

    // ---------- Otras pantallas ----------

    var statsAllTime by mutableStateOf(false)
    var panelFilter by mutableStateOf("")
    val spotify = SpotifyImportState()

    /** «Pegar playlist de Spotify» / «Importar de Spotify»: valida el enlace y empareja (simulado). */
    fun startSpotifyImport() {
        val sp = spotify
        when (Spotify.parse(sp.link)) {
            SpotifyLink.Invalid -> sp.error = "Eso no parece un enlace de playlist de Spotify. En Spotify: Compartir → Copiar enlace."
            SpotifyLink.Editorial -> sp.error = "Esa playlist la hace Spotify y no se puede importar. Prueba con una tuya."
            is SpotifyLink.Playlist -> {
                if (!online) {
                    sp.error = "Sin conexión: importar vuelve cuando haya internet."
                    return
                }
                sp.error = null
                sp.phase = ImportPhase.Matching
                sp.checked = 0
                val tracks = SampleData.spotifyTracks
                sp.total = tracks.size
                scope?.launch {
                    for (i in tracks.indices) {
                        delay(180)
                        sp.checked = i + 1
                    }
                    val matches = Spotify.matchAll(tracks, SampleData.catalog)
                    sp.matches.clear()
                    sp.matches.addAll(matches)
                    sp.decisions.clear()
                    val pid = createPlaylist(SampleData.SPOTIFY_PLAYLIST_NAME)
                    sp.playlistId = pid
                    acceptMatches(pid, matches.filter { it.kind == MatchKind.Segura }.mapNotNull { it.result })
                    sp.phase = ImportPhase.Done
                }
            }
        }
    }

    /** Confirmar (✓) o descartar (✗) una coincidencia dudosa. */
    fun decideMatch(index: Int, accept: Boolean) {
        val sp = spotify
        if (sp.decisions.containsKey(index)) return
        sp.decisions[index] = accept
        val pid = sp.playlistId ?: return
        val r = sp.matches.getOrNull(index)?.result ?: return
        if (accept) acceptMatches(pid, listOf(r))
    }

    private fun acceptMatches(pid: String, results: List<SearchResult>) {
        val have = results.mapNotNull { librarySongFor(it) }
        if (have.isNotEmpty()) addToPlaylist(pid, have.map { it.id })
        val missing = results.filter { librarySongFor(it) == null && downloadFraction(it.id) == null }
        if (missing.isEmpty()) return
        missing.forEach { if (it.id !in addedIds) addedIds.add(it.id) }
        enqueue(DownloadJob(newId(), playlist(pid)?.name ?: "", missing.map { songFor(it) }, pid, isCollection = true))
    }

    fun setDemoEmpty(on: Boolean) {
        settings.demoEmpty = on
        if (on) {
            isPlaying = false
            dropPreview()
        }
    }

    // ---------- Avisos ----------

    var toast by mutableStateOf<Toast?>(null)
        private set
    var error by mutableStateOf<ErrorNotice?>(null)
        private set

    fun toast(
        text: String,
        mood: Mood = Mood.Owo,
        tone: Tone = Tone.Pink,
        actionLabel: String? = null,
        millis: Long = 2600,
        action: (() -> Unit)? = null,
    ) {
        val t = Toast(newId(), text, mood, tone, actionLabel, action, if (action != null) maxOf(millis, 4500) else millis)
        toast = t
        scope?.launch {
            delay(t.millis)
            if (toast?.id == t.id) toast = null
        }
    }

    /** Lo que todavía no existe (ecualizador, temporizador, QR…): avisa de que llega más adelante. */
    fun soon(what: String) = toast("$what llega en una próxima versión.", Mood.Dormido, Tone.Bone)

    fun runToastAction() {
        val t = toast ?: return
        toast = null
        t.action?.invoke()
    }

    fun dismissToast() {
        toast = null
    }

    fun showError(title: String, body: String, retry: (() -> Unit)?) {
        error = ErrorNotice(newId(), title, body, retry)
    }

    fun dismissError() {
        error = null
    }

    fun retryError() {
        val e = error ?: return
        error = null
        e.retry?.invoke()
    }

    // ---------- Motor ----------

    private var nextId = 1L
    private var scope: CoroutineScope? = null

    private fun newId(): Long = nextId++

    private fun replaceSong(id: String, change: (Song) -> Song) {
        val i = songs.indexOfFirst { it.id == id }
        if (i >= 0) songs[i] = change(songs[i])
    }

    /**
     * Hace correr el tiempo: reproducción, vista previa, avisos y descargas.
     * La app lo lanza al empezar (LaunchedEffect) y se para al cerrarla.
     */
    suspend fun run() {
        coroutineScope {
            scope = this
            try {
                var mark = TimeSource.Monotonic.markNow()
                while (isActive) {
                    delay(100)
                    val elapsed = mark.elapsedNow().inWholeMilliseconds
                    mark = TimeSource.Monotonic.markNow()
                    advance(elapsed.coerceIn(0L, 1_000L))
                }
            } finally {
                scope = null
                worker = null
            }
        }
    }

    /** Fuente de la cola para un resultado de búsqueda de vídeo (para el título de «SONANDO»). */
    fun isVideo(r: SearchResult): Boolean = r.kind == ResultKind.Video
}
