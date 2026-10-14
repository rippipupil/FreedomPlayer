package com.freedomplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import com.freedomplayer.core.AlbumResult
import com.freedomplayer.core.AppRelease
import com.freedomplayer.core.AppVersions
import com.freedomplayer.core.AudioQuality
import com.freedomplayer.core.CoverPalette
import com.freedomplayer.core.Crossfade
import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.EqBands
import com.freedomplayer.core.GhostCollection
import com.freedomplayer.core.GhostFacts
import com.freedomplayer.core.GhostSkin
import com.freedomplayer.core.Loudness
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.OtherList
import com.freedomplayer.core.MatchKind
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.PlayQueue
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.PlaylistOrder
import com.freedomplayer.core.PlaylistResult
import com.freedomplayer.core.PlaylistSort
import com.freedomplayer.core.Ranking
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.Search
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.Song
import com.freedomplayer.core.Spotify
import com.freedomplayer.core.SpotifyLink
import com.freedomplayer.core.Stats
import com.freedomplayer.core.Tempo
import com.freedomplayer.core.SyncIds
import com.freedomplayer.core.TrackMatch
import com.freedomplayer.core.Wrapped
import com.freedomplayer.core.WrappedPeriod
import com.freedomplayer.core.WrappedReport
import com.freedomplayer.core.YearMonth
import com.freedomplayer.core.YouTubeLinks
import com.freedomplayer.core.folderName
import com.freedomplayer.core.minutesText
import com.freedomplayer.core.starsText
import com.freedomplayer.data.DownloadRequest
import com.freedomplayer.data.DownloadedSong
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.LibraryData
import com.freedomplayer.data.Services
import com.freedomplayer.data.SoundSettings
import com.freedomplayer.data.SpotifyReader
import com.freedomplayer.data.SyncBackend
import com.freedomplayer.data.SyncUnavailableException
import com.freedomplayer.data.Track
import com.freedomplayer.data.demo
import com.freedomplayer.data.explain
import com.freedomplayer.data.failureKind
import com.freedomplayer.ui.components.Mood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.TimeSource

/** Pantallas. Las cinco primeras son las del menú de los paneles. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Rank : Screen
    data object Stats : Screen
    data object Settings : Screen
    data class PlaylistDetail(val id: String) : Screen

    /** Una lista de otra persona (YouTube Music o YouTube), para añadir canciones sueltas. */
    data class OtherListDetail(val url: String) : Screen

    /** Un álbum del buscador: sus canciones una a una, para escucharlas o añadir las que quieras. */
    data class AlbumDetail(val key: String) : Screen
    data object SpotifyImport : Screen
    data object Sync : Screen

    /** El resumen de un mes o un año en tarjetas, para guardarlas o compartirlas. */
    data object Wrapped : Screen

    /** Los fantasmas coleccionables: los ganados (para ponérselos) y lo que falta para los demás. */
    data object Ghosts : Screen

    val inMenu: Boolean get() = this == Home || this == Search || this == Rank || this == Stats || this == Settings
}

enum class Side { Left, Right }

enum class SearchTab { Songs, Albums, Videos, Playlists }

enum class RankTab { General, Mine, Unrated }

/** Color de la raya de un aviso. */
enum class Tone { Pink, Jade, Neon, Bone }

/** Lo que se quiere añadir al tocar «+». */
sealed interface AddTarget {
    data class Result(val result: SearchResult) : AddTarget
    data class Album(val album: AlbumResult) : AddTarget

    /** «+ AÑADIR TODAS» de una lista de otra persona. */
    data class WholeList(val list: OtherList) : AddTarget
    /** Una canción que ya está en la biblioteca (desde el reproductor): solo se elige playlist. */
    data class LibrarySong(val songId: String) : AddTarget
}

sealed interface Sheet {
    /** El selector de «+»: una playlist o solo la biblioteca. */
    data class Add(val target: AddTarget) : Sheet

    /** «⋯» del reproductor. */
    data object More : Sheet

    /** Elegir a qué ranking personal va una canción. */
    data class AddToRanking(val songId: String) : Sheet

    /** La cola: lo que suena y lo que viene, para reordenar o quitar. */
    data object Queue : Sheet

    /** Temporizador de apagado. */
    data object SleepTimer : Sheet

    /** Velocidad y tono. */
    data object Speed : Sheet

    /** Ecualizador de 5 bandas. */
    data object Equalizer : Sheet
}

/** Pestañas de Estadísticas. */
enum class StatsTab { Recent, Monthly, AllTime }

/** Temporizador de apagado: para al acabar la canción que suena ([endOfSong]) o a los [remainingMs]. */
data class SleepTimer(val minutes: Int, val remainingMs: Long, val endOfSong: Boolean = false)

sealed interface Prompt {
    /** Crear una playlist y, si hace falta, añadir después lo que se estaba añadiendo. */
    data class NewPlaylist(val then: AddTarget? = null, val title: String? = null, val artist: String? = null) : Prompt
    data object NewRanking : Prompt

    /** Ranking nuevo que empieza con una canción (desde «⋯» del reproductor). */
    data class NewRankingWith(val songId: String) : Prompt

    /** Corregir el título y el artista de una canción (los de un vídeo, por ejemplo). */
    data class EditSong(val songId: String) : Prompt
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
data class Preview(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val coverColor: Long,
    val cover: String? = null,
)

/** Una descarga: una canción, un álbum o lo que falta de una playlist importada. */
class DownloadJob(
    val id: Long,
    val title: String,
    val playlistId: String?,
    val isCollection: Boolean,
    /** El álbum del que sale (para su fila en Buscar). */
    val albumKey: String? = null,
) {
    val items = mutableStateListOf<DownloadRequest>()

    /** Todavía se están buscando las canciones del álbum. */
    var resolving by mutableStateOf(false)
    var done by mutableIntStateOf(0)
    var itemProgress by mutableFloatStateOf(0f)
    var failed by mutableIntStateOf(0)
    val total: Int get() = items.size
    val fraction: Float get() = if (total == 0) 0f else ((done + itemProgress) / total).coerceIn(0f, 1f)
}

/** Resultados de una pestaña del buscador. */
data class TabResults<T>(
    val query: String = "",
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: FailureKind? = null,
)

enum class ImportPhase { Paste, Matching, Done }

/** Una lista de otra persona abierta: cargando, con su error o con sus canciones. */
class OtherListState(val url: String, val hint: PlaylistResult?) {
    var list by mutableStateOf<OtherList?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<FailureKind?>(null)

    /** Lo que se sabe de la lista: lo cargado o, mientras tanto, lo que enseñaba el buscador. */
    val info: PlaylistResult? get() = list?.info ?: hint
}

/** Segundos que saltan «⟲» y «⟳» si no se ha elegido otra cosa en Ajustes. */
const val SEEK_STEP_DEFAULT = 10

/** Lo que se puede elegir en Ajustes para «⟲» y «⟳». */
val SEEK_STEPS = listOf(5, 10, 15, 30)

/** Dos toques en «atrás» del mini-reproductor dentro de este tiempo van a la canción anterior. */
const val REWIND_DOUBLE_TAP_MS = 600L

/** Cada cuánto se mira si hay versión nueva mientras la app está abierta (6 h). */
private const val UPDATE_CHECK_EVERY_MS = 6L * 60 * 60 * 1000
private const val SYNC_PEER = "sync.peer"

private const val SPOTIFY_EDITORIAL = "Esa playlist la hace Spotify y no se puede importar. Prueba con una tuya."

/** En qué va la sincronización con el otro dispositivo. */
enum class SyncPhase { Off, Starting, Waiting, Pairing, Syncing, Synced, Failed }

/**
 * Si Android tiene que mantener viva la app (el servicio en primer plano de «sincronización de
 * datos», con su notificación): solo mientras arranca Syncthing, se vincula o se pasan canciones.
 * Esperando el QR, ya sincronizado, parado o con error no hace falta, y Android 15 solo da a ese
 * servicio 6 horas al día con la app en segundo plano.
 */
val SyncPhase.needsForeground: Boolean
    get() = this == SyncPhase.Starting || this == SyncPhase.Pairing || this == SyncPhase.Syncing

/** El aviso de cuando Android para la sincronización con la app en segundo plano. */
const val SYNC_STOPPED_BY_ANDROID =
    "Android ha pausado la sincronización: con la app en segundo plano solo la deja seguir unas horas al día. Toca REINTENTAR para seguir."

/** «Vincular»: Syncthing, el QR (en el PC), el otro dispositivo y cuánto lleva. */
class SyncState {
    var phase by mutableStateOf(SyncPhase.Off)
    var myId by mutableStateOf<String?>(null)
    var qr by mutableStateOf<List<BooleanArray>?>(null)
    var peer by mutableStateOf<String?>(null)
    var done by mutableStateOf(0L)
    var total by mutableStateOf(0L)
    var error by mutableStateOf<String?>(null)

    /** Ya vinculados: si ahora mismo está conectado con el otro dispositivo. */
    var connected by mutableStateOf(false)
}

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

    /** Lo que conviene saber del resultado (Spotify enseña menos canciones, búsquedas que han fallado). */
    var note by mutableStateOf<String?>(null)

    fun reset() {
        link = ""
        error = null
        note = null
        phase = ImportPhase.Paste
        checked = 0
        total = 0
        matches.clear()
        decisions.clear()
        playlistId = null
    }
}

/** Ajustes. Se guardan en la base de datos al cambiarlos (menos los de «Probar estados»). */
class Settings(private val read: (String) -> String? = { null }, private val write: (String, String?) -> Unit = { _, _ -> }) {
    private inner class Flag(private val key: String, default: Boolean) : ReadWriteProperty<Any?, Boolean> {
        private val state = mutableStateOf(read(key)?.toBooleanStrictOrNull() ?: default)
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            if (state.value == value) return
            state.value = value
            write(key, value.toString())
        }
    }

    private inner class Whole(private val key: String, default: Int, private val allowed: IntRange) : ReadWriteProperty<Any?, Int> {
        private val state = mutableStateOf(read(key)?.toIntOrNull()?.takeIf { it in allowed } ?: default)
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            val v = value.coerceIn(allowed)
            if (state.value == v) return
            state.value = v
            write(key, v.toString())
        }
    }

    var normalizeVolume by Flag("normalizeVolume", true)
    var crossfade by Flag("crossfade", false)

    /** Segundos del fundido entre canciones. */
    var crossfadeSeconds by Whole("crossfadeSeconds", Crossfade.DEFAULT_SECONDS, 1..12)

    private var speedPercent by Whole("tempo.speed", 100, 50..200)
    private var pitchSemitones by Whole("tempo.semitones", 0, -6..6)
    private var pitchWithSpeed by Flag("tempo.pitchWithSpeed", false)

    /** Velocidad y tono de lo que suena (el panel «Velocidad» del reproductor). Se recuerda. */
    var tempo: Tempo
        get() = Tempo(speedPercent, pitchSemitones, pitchWithSpeed)
        set(value) {
            speedPercent = value.speedPercent
            pitchSemitones = value.semitones
            pitchWithSpeed = value.pitchWithSpeed
        }

    /** Segundos que saltan «⟲» y «⟳» (Ajustes › Reproducción: 5, 10, 15 o 30). */
    var seekSeconds by Whole("seekSeconds", SEEK_STEP_DEFAULT, 5..30)

    private val eqState = mutableStateOf(EqBands.parse(read("eq.gains")))

    /** Ecualizador: dB de cada banda (todo a 0, «Plano», es no tocar el sonido). */
    var eqGains: List<Float>
        get() = eqState.value
        set(value) {
            val v = EqBands.clamp(value)
            if (v == eqState.value) return
            eqState.value = v
            write("eq.gains", EqBands.format(v))
        }
    var syncedLyrics by Flag("syncedLyrics", true)
    var keepOffline by Flag("keepOffline", true)
    var closeToTray by Flag("closeToTray", true)
    var miniOnTop by Flag("miniOnTop", true)

    private val qualityState = mutableStateOf(AudioQuality.parse(read("download.quality")))

    /** Calidad del audio que se descarga (por defecto, la mejor que da YouTube). */
    var downloadQuality: AudioQuality
        get() = qualityState.value
        set(value) {
            if (value == qualityState.value) return
            qualityState.value = value
            write("download.quality", value.name)
        }

    private val folderState = mutableStateOf(read("download.folder")?.takeIf { it.isNotBlank() })

    /** Carpeta para las descargas nuevas (solo en el PC); null: la de la app. */
    var downloadFolder: String?
        get() = folderState.value
        set(value) {
            val v = value?.takeIf { it.isNotBlank() }
            if (v == folderState.value) return
            folderState.value = v
            write("download.folder", v)
        }

    private val orderState = mutableStateOf(PlaylistOrder.decode(read("playlist.order")))

    /** Orden de las canciones dentro de las playlists (por fecha o por nota; de mayor a menor o al revés). */
    var playlistOrder: PlaylistOrder
        get() = orderState.value
        set(value) {
            if (value == orderState.value) return
            orderState.value = value
            write("playlist.order", value.encode())
        }

    private val skinState = mutableStateOf(GhostSkin.entries.firstOrNull { it.name == read("ghost.skin") } ?: GhostSkin.Clasico)

    /** El fantasma coleccionable que se lleva puesto. */
    var ghostSkin: GhostSkin
        get() = skinState.value
        set(value) {
            if (value == skinState.value) return
            skinState.value = value
            write("ghost.skin", value.name)
        }

    private val announcedState = mutableStateOf(
        read("ghost.announced")?.split(',')?.mapNotNull { n -> GhostSkin.entries.firstOrNull { it.name == n } }?.toSet(),
    )

    /**
     * Los fantasmas ganados de los que ya se ha avisado (null: nunca se ha mirado, y entonces los
     * que ya estén ganados se apuntan sin avisar, para no soltar diez avisos al actualizar).
     */
    var ghostsAnnounced: Set<GhostSkin>?
        get() = announcedState.value
        set(value) {
            if (value == announcedState.value) return
            announcedState.value = value
            write("ghost.announced", value?.joinToString(",") { it.name })
        }

    /** Si ya se ha abierto el Wrapped alguna vez (gana el fantasma «Fiesta»). */
    var wrappedSeen by Flag("wrapped.seen", false)

    /** Para probar los estados sin red y con la biblioteca vacía (no se guardan). */
    var demoOffline by mutableStateOf(false)
    var demoEmpty by mutableStateOf(false)
}

/**
 * Todo el estado de la app, igual en el móvil y en el PC. Lo de fuera (biblioteca guardada,
 * buscador, descargas, letras y audio) llega en [services]; sin ellos se usan los datos de
 * ejemplo del diseño (pruebas y capturas). Los temporizadores corren mientras [run] está activo.
 */
class AppState(
    startMillis: Long = nowMillis(),
    val services: Services = Services.demo(startMillis),
) {
    private val store = services.store
    private val engine = services.engine

    /** Datos de ejemplo (pruebas y capturas): biblioteca, buscador y audio inventados. */
    val isDemo: Boolean get() = services.demo

    // ---------- Biblioteca ----------

    private val loaded = store.load()
    val songs = mutableStateListOf<Song>().apply { addAll(loaded.songs) }
    val playlists = mutableStateListOf<Playlist>().apply { addAll(loaded.playlists) }
    val rankings = mutableStateListOf<CustomRanking>().apply { addAll(loaded.rankings) }
    val playLog = mutableStateListOf<PlayEvent>().apply { addAll(loaded.playLog) }
    val settings = Settings(read = store::setting, write = store::setSetting)

    /** Sube cada vez que cambia el historial o una nota (para volver a pedir Recientes y Más escuchadas). */
    var historyVersion by mutableIntStateOf(0)
        private set

    /** La biblioteca que se ve (vacía si se está probando «Biblioteca vacía»). */
    val library: List<Song> get() = if (settings.demoEmpty) emptyList() else songs

    fun song(id: String?): Song? = if (id == null) null else songs.firstOrNull { it.id == id }

    fun playlist(id: String?): Playlist? = if (id == null) null else playlists.firstOrNull { it.id == id }

    /** Las canciones de una playlist en el orden elegido (Ajustes › orden de las playlists). */
    fun playlistSongs(p: Playlist): List<Song> = settings.playlistOrder.sort(p, p.songIds.mapNotNull { song(it) })

    /** FECHA / NOTA en una playlist: tocar el elegido le da la vuelta. */
    fun sortPlaylistsBy(sort: PlaylistSort) {
        settings.playlistOrder = settings.playlistOrder.tap(sort)
    }

    /** El botón de la flecha: de mayor a menor o de menor a mayor. */
    fun flipPlaylistOrder() {
        settings.playlistOrder = settings.playlistOrder.flipped()
    }

    /** Las carátulas de las primeras canciones de una playlist (para sus carátulas apiladas). */
    fun coversOf(p: Playlist): List<String> = p.songIds.asSequence().mapNotNull { song(it)?.cover }.take(2).toList()

    /** Las consultas del historial, guardadas hasta que cambie algo (Inicio se repinta a menudo). */
    private val historyCache = HashMap<String, Pair<Int, Any>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cachedHistory(key: String, query: () -> T): T {
        val version = historyVersion
        historyCache[key]?.let { (v, value) -> if (v == version) return value as T }
        return query().also { historyCache[key] = version to it }
    }

    /** «Recientes»: lo último que ha sonado (del historial guardado). */
    fun recentSongs(limit: Int): List<Song> {
        historyVersion
        if (settings.demoEmpty) return emptyList()
        return cachedHistory("recent:$limit") { store.recentlyPlayed(limit) }.mapNotNull { song(it) }
    }

    /** «Más escuchadas»: escuchas de los últimos [days] días. */
    fun mostPlayed(days: Int, top: Int): List<Pair<Song, Int>> {
        historyVersion
        if (settings.demoEmpty) return emptyList()
        // La ventana de días se mueve sola: con la hora en la clave, se vuelve a pedir cada hora.
        val hour = nowMillis() / (60 * 60 * 1000L)
        return cachedHistory("most:$days:$top:$hour") { store.mostPlayedSince(nowMillis() - days * Stats.DAY_MS, top) }
            .mapNotNull { (id, n) -> song(id)?.let { it to n } }
    }

    // ---------- Plataforma ----------

    /** Lo pone la plataforma (Android: ConnectivityManager; PC: interfaces de red). */
    var systemOnline by mutableStateOf(true)

    /** Lo pone la plataforma: el sistema pide reducir el movimiento. */
    var reduceMotion by mutableStateOf(false)

    val online: Boolean get() = systemOnline && !settings.demoOffline

    /** Abre el selector de archivos de audio del sistema (solo donde se puede: Android). */
    var fileImporter: (() -> Unit)? by mutableStateOf(null)

    val canImportFiles: Boolean get() = fileImporter != null

    /**
     * Guarda o comparte una imagen (las tarjetas del Wrapped) con este nombre de archivo: el PC la
     * guarda en Imágenes y devuelve dónde; el móvil abre «Compartir» y devuelve null. Si falla, lanza.
     */
    var imageSharer: ((ImageBitmap, String) -> String?)? by mutableStateOf(null)

    val canShareImages: Boolean get() = imageSharer != null

    fun shareImage(image: ImageBitmap, fileName: String) {
        val share = imageSharer ?: return
        try {
            val where = share(image, fileName)
            if (where != null) toast("Imagen guardada en $where", Mood.Feliz, Tone.Jade, millis = 4_000)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            toast("No se pudo guardar la imagen.", Mood.Triste, Tone.Bone)
        }
    }

    /** El tono se puede cambiar aparte de la velocidad (en el PC va con ella). */
    val separatePitch: Boolean get() = engine.separatePitch

    /**
     * Abre el selector de carpetas del sistema para las descargas. Solo lo pone el PC: en Android
     * la música se queda en la carpeta de la app (la sincronización necesita una ruta fija).
     */
    var folderPicker: (() -> Unit)? by mutableStateOf(null)

    val canChooseFolder: Boolean get() = folderPicker != null

    /** La carpeta de la app donde va la música si no se elige otra (la pone la plataforma). */
    var appMusicFolder: String? by mutableStateOf(null)

    /** Sincronizar con el otro dispositivo. */
    val sync = SyncState()

    /** El escáner de QR del móvil (lo pone la actividad de Android); null si aquí no hay. */
    var qrScanner: (((String?) -> Unit) -> Unit)? by mutableStateOf(null)

    val canSync: Boolean get() = services.sync != null

    private var syncWatch: Job? = null

    /**
     * Cuánto se espera a que aparezca el otro dispositivo antes de avisar de que no se encuentra.
     * Syncthing reintenta conectar cada minuto, así que algo más de uno.
     */
    internal var syncConnectTimeoutMs = 90_000L

    // ---------- Navegación ----------

    val history = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = history.lastOrNull() ?: Screen.Home

    /** Hacia dónde va la última navegación (para la dirección de la transición). */
    var navForward by mutableStateOf(true)
        private set
    var panel by mutableStateOf<Side?>(null)
    var playerOpen by mutableStateOf(false)

    /** La letra a pantalla completa, encima del reproductor. */
    var lyricsOpen by mutableStateOf(false)
    var sheet by mutableStateOf<Sheet?>(null)
    var prompt by mutableStateOf<Prompt?>(null)

    val canGoBack: Boolean
        get() = prompt != null || sheet != null || panel != null || playerOpen || history.size > 1

    fun go(target: Screen) {
        panel = null
        sheet = null
        playerOpen = false
        lyricsOpen = false
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
            lyricsOpen -> lyricsOpen = false
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

    // ---------- Reproducción ----------

    var queue by mutableStateOf(PlayQueue())
        private set

    /** Qué está sonando («NOCHES DE NEÓN», «TU BIBLIOTECA»…), para «SONANDO · …». */
    var queueSource by mutableStateOf("")
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set

    /** Duración real de lo que suena (la del archivo), o 0 si aún no se sabe. */
    var durationMs by mutableLongStateOf(0L)
        private set

    /** Sube con cada play, pausa o cambio de canción: los fantasmas dan un saltito. */
    var reaction by mutableIntStateOf(0)
        private set

    private var volumeState by mutableFloatStateOf(store.setting("volume")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.75f)
    private var mutedState by mutableStateOf(false)

    /** Volumen de la app (barra del PC). */
    var volume: Float
        get() = volumeState
        set(value) {
            volumeState = value.coerceIn(0f, 1f)
            store.setSetting("volume", volumeState.toString())
            applyVolume()
        }

    var muted: Boolean
        get() = mutedState
        set(value) {
            mutedState = value
            applyVolume()
        }

    private fun applyVolume() = engine.setVolume(if (mutedState) 0f else volumeState)

    val currentSong: Song? get() = if (settings.demoEmpty) null else song(queue.current)

    /** Duración de lo que suena en segundos: la del archivo si se sabe, o la de la biblioteca. */
    val currentDurationSec: Int
        get() = if (durationMs > 0) ((durationMs + 500) / 1000).toInt() else currentSong?.durationSec ?: 0

    /** Se puede reproducir: ya está descargada (o son datos de ejemplo). */
    fun canPlay(song: Song): Boolean = isDemo || song.audioPath != null

    private fun Song.toTrack(): Track =
        Track(id, audioPath ?: "demo:$id", title, artist, album, cover, durationSec * 1000L, gainDb = Loudness.gainDb(loudnessOf(id)))

    // ---------- Cómo suena: ecualizador, volumen igualado y fundido ----------

    /** Lo último que se le ha dicho al motor (para decírselo solo cuando cambia). */
    private var appliedSound: SoundSettings? = null

    private fun applySound() {
        val s = SoundSettings(
            eqGains = settings.eqGains,
            normalize = settings.normalizeVolume,
            crossfadeMs = if (settings.crossfade) settings.crossfadeSeconds * 1000L else 0L,
            speed = settings.tempo.speed,
            pitch = if (engine.separatePitch) settings.tempo.pitch else settings.tempo.speed,
        )
        if (s == appliedSound) return
        appliedSound = s
        engine.setSound(s)
    }

    /** Mueve una banda del ecualizador (las demás se quedan como están). */
    fun setEqBand(band: Int, db: Float) {
        val g = settings.eqGains.toMutableList()
        if (band !in g.indices) return
        g[band] = db
        settings.eqGains = g
    }

    val equalizerAvailable: Boolean get() = engine.equalizerAvailable

    /** Sonoridad medida de cada canción (LUFS); se guarda en los ajustes como «loudness.<id>». */
    private val loudness = HashMap<String, Float?>()

    /** Las que no se han podido medir (no se reintentan hasta volver a abrir la app). */
    private val unmeasurable = HashSet<String>()

    private fun loudnessOf(id: String): Float? {
        if (id in loudness) return loudness[id]
        val v = store.setting("loudness.$id")?.toFloatOrNull()
        loudness[id] = v
        return v
    }

    /** La siguiente canción por medir: primero lo que va a sonar, luego el resto de la biblioteca. */
    private fun nextToMeasure(): String? {
        val order = queue.upcoming.take(5) + listOfNotNull(queue.current) + songs.map { it.id }
        return order.firstOrNull { id -> id !in unmeasurable && song(id)?.audioPath != null && loudnessOf(id) == null }
    }

    /** Mide, de una en una y sin prisa, lo fuerte que suena cada canción (para el volumen igualado). */
    private suspend fun measureLoudness() {
        val analyzer = services.loudness ?: return
        while (true) {
            val id = if (settings.normalizeVolume) nextToMeasure() else null
            if (id == null) {
                delay(5_000)
                continue
            }
            val path = song(id)?.audioPath
            val lufs = try {
                if (path == null) null else analyzer.measure(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            if (lufs == null) {
                unmeasurable += id
            } else {
                loudness[id] = lufs
                store.setSetting("loudness.$id", lufs.toString())
                // Las que vienen ya suenan igualadas (la que suena, desde la próxima vez).
                if (id in queue.upcoming) engine.reorder(tracks(queue.items), queue.index)
            }
            delay(200)
        }
    }

    private fun tracks(ids: List<String>): List<Track> = ids.mapNotNull { song(it)?.toTrack() }

    /** Toca una canción de una lista: suena y la cola sigue con el resto de esa lista. */
    fun playFrom(ids: List<String>, startId: String, source: String) {
        val playable = ids.filter { id -> song(id)?.let(::canPlay) == true }
        if (playable.isEmpty()) return
        dropPreview()
        queue = PlayQueue.of(playable, startId, shuffle = queue.shuffle, repeat = queue.repeat)
        queueSource = source
        startQueue()
    }

    /** REPRODUCIR / ALEATORIO de una playlist. */
    fun playPlaylist(id: String, shuffle: Boolean) {
        val p = playlist(id) ?: return
        val ids = playlistSongs(p).filter(::canPlay).map { it.id }
        if (ids.isEmpty()) return
        dropPreview()
        queue = PlayQueue.of(ids, if (shuffle) ids.random() else ids.first(), shuffle = shuffle, repeat = queue.repeat)
        queueSource = p.name.uppercase()
        startQueue()
    }

    private fun startQueue() {
        engine.setRepeat(queue.repeat)
        engine.load(tracks(queue.items), queue.index, 0, play = true)
        positionMs = 0
        durationMs = 0
        isPlaying = true
        resetCount()
        reaction++
        saveQueue()
    }

    fun togglePlay() {
        if (preview != null) {
            if (previewPlaying) engine.previewPause() else engine.previewPlay()
            previewPlaying = !previewPlaying
            reaction++
            return
        }
        if (currentSong == null) return
        if (isPlaying) engine.pause() else engine.play()
        isPlaying = !isPlaying
        reaction++
        saveQueue()
    }

    fun next() {
        if (preview != null) {
            stopPreview()
            return
        }
        if (queue.isEmpty) return
        // Después de la última viene la primera: la cola nunca se para.
        val n = queue.next() ?: return
        queue = n
        engine.skipTo(n.index)
        positionMs = 0
        durationMs = 0
        resetCount()
        reaction++
        saveQueue()
    }

    fun previous() {
        if (preview != null) {
            engine.previewSeek(0)
            previewPositionMs = 0
            return
        }
        if (queue.isEmpty) return
        if (positionMs > 3_000) {
            engine.seekTo(0)
            positionMs = 0
        } else {
            queue = queue.previous()
            engine.skipTo(queue.index)
            positionMs = 0
            durationMs = 0
            resetCount()
        }
        reaction++
        saveQueue()
    }

    private var lastRewindAt = Long.MIN_VALUE / 2

    /**
     * Botón «atrás» del mini-reproductor: un toque vuelve al principio de la canción; otro toque
     * enseguida ([REWIND_DOUBLE_TAP_MS]) pasa a la anterior, y cada toque rápido más sigue hacia
     * atrás. Si no hay anterior, vuelve a empezar la misma.
     */
    fun rewind(atMillis: Long = nowMillis()) {
        val quick = atMillis - lastRewindAt in 0..REWIND_DOUBLE_TAP_MS
        lastRewindAt = atMillis
        if (preview != null) {
            engine.previewSeek(0)
            previewPositionMs = 0
            reaction++
            return
        }
        if (queue.isEmpty) return
        // En la primera no hay anterior: vuelve a empezar la misma (no salta a la última).
        val back = if (quick && queue.index > 0) queue.previous() else queue
        if (back.index != queue.index) {
            queue = back
            engine.skipTo(back.index)
            positionMs = 0
            durationMs = 0
            resetCount()
        } else {
            engine.seekTo(0)
            positionMs = 0
        }
        reaction++
        saveQueue()
    }

    fun seekTo(fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        val p = preview
        if (p != null) {
            val total = if (previewDurationMs > 0) previewDurationMs else p.durationSec * 1000L
            previewPositionMs = (total * f).toLong()
            engine.previewSeek(previewPositionMs)
            return
        }
        currentSong ?: return
        positionMs = (currentDurationSec * 1000L * f).toLong()
        engine.seekTo(positionMs)
    }

    /**
     * «⟲ 10» / «10 ⟳»: atrás o adelante los segundos de Ajustes. No pasa de canción: se queda
     * entre el principio y el final de la que suena (también en la vista previa).
     */
    fun seekBy(forward: Boolean) {
        val step = settings.seekSeconds * 1000L * (if (forward) 1 else -1)
        val p = preview
        if (p != null) {
            val total = if (previewDurationMs > 0) previewDurationMs else p.durationSec * 1000L
            previewPositionMs = (previewPositionMs + step).coerceIn(0L, (total - 1_000).coerceAtLeast(0L))
            engine.previewSeek(previewPositionMs)
            return
        }
        currentSong ?: return
        val total = currentDurationSec * 1000L
        positionMs = (positionMs + step).coerceIn(0L, (total - 1_000).coerceAtLeast(0L))
        engine.seekTo(positionMs)
        saveQueue()
    }

    fun toggleShuffle() {
        queue = queue.toggleShuffle()
        if (!queue.isEmpty) engine.reorder(tracks(queue.items), queue.index)
        saveQueue()
    }

    fun cycleRepeat() {
        queue = queue.cycleRepeat()
        engine.setRepeat(queue.repeat)
        saveQueue()
    }

    // ---------- La cola ----------

    /** Toca una canción de la cola: suena desde el principio. */
    fun playQueueItem(position: Int) {
        if (position !in queue.items.indices) return
        dropPreview()
        queue = queue.jumpTo(position)
        engine.skipTo(position)
        if (!isPlaying) {
            engine.play()
            isPlaying = true
        }
        positionMs = 0
        durationMs = 0
        resetCount()
        reaction++
        saveQueue()
    }

    /** Cambia de sitio una canción de la cola; la que suena sigue sonando. */
    fun moveInQueue(from: Int, to: Int) {
        val moved = queue.move(from, to)
        if (moved == queue) return
        queue = moved
        engine.reorder(tracks(queue.items), queue.index)
        saveQueue()
    }

    /** Quita una canción de la cola (no de la biblioteca). La que suena no se quita. */
    fun removeFromQueue(position: Int) {
        val before = queue
        val removed = queue.remove(position)
        if (removed == before) return
        val title = song(before.items[position])?.title
        queue = removed
        engine.reorder(tracks(queue.items), queue.index)
        saveQueue()
        toast("Quitada de la cola${title?.let { ": «$it»" } ?: ""}.", Mood.Owo, actionLabel = "DESHACER") {
            if (queue.items.size == removed.items.size && queue.current == removed.current) {
                queue = before.copy(index = before.items.indexOf(queue.current).takeIf { it >= 0 } ?: queue.index)
                engine.reorder(tracks(queue.items), queue.index)
                saveQueue()
            }
        }
    }

    // ---------- Temporizador de apagado ----------

    var sleepTimer by mutableStateOf<SleepTimer?>(null)
        private set

    fun startSleepTimer(minutes: Int) {
        engine.setPauseAtEndOfSong(false)
        sleepTimer = SleepTimer(minutes, minutes * 60_000L)
        sheet = null
        toast("La música se parará dentro de ${minutesText(minutes)}.", Mood.Dormido, Tone.Bone)
    }

    /** Se para al acabar la canción que suena (si se cambia de canción, al acabar esa). */
    fun sleepAtEndOfSong() {
        engine.setPauseAtEndOfSong(true)
        sleepTimer = SleepTimer(0, 0, endOfSong = true)
        sheet = null
        toast("La música se parará al acabar esta canción.", Mood.Dormido, Tone.Bone)
    }

    fun cancelSleepTimer() {
        if (sleepTimer == null) return
        engine.setPauseAtEndOfSong(false)
        sleepTimer = null
        sheet = null
        toast("Temporizador apagado.", Mood.Owo, Tone.Bone)
    }

    private fun tickSleep(elapsedMs: Long) {
        val t = sleepTimer ?: return
        if (t.endOfSong) return
        val left = t.remainingMs - elapsedMs
        if (left > 0) {
            sleepTimer = t.copy(remainingMs = left)
            return
        }
        sleepTimer = null
        goToSleep()
    }

    /** Se acabó el tiempo: pausa (también una vista previa) y da las buenas noches. */
    private fun goToSleep() {
        val wasPlaying = isPlaying || preview != null
        dropPreview()
        if (isPlaying) {
            engine.pause()
            isPlaying = false
            reaction++
            saveQueue()
        }
        if (wasPlaying) toast("Buenas noches: el temporizador ha parado la música.", Mood.Dormido, Tone.Bone)
    }

    /** Escuchado de la canción actual (cuenta como escucha a los 30 s, o a la mitad si es corta). */
    private var listenedMs = 0L
    private var counted = false
    private var lastTransitions = Int.MIN_VALUE
    private var lastPosition = 0L
    private var lastErrorSerial = 0

    private fun resetCount() {
        listenedMs = 0
        counted = false
        lastPosition = 0
    }

    private fun recordPlay(s: Song) {
        val e = PlayEvent(s.id, nowMillis())
        playLog.add(e)
        store.addPlay(e)
        replaceSong(s.id) { it.copy(plays = it.plays + 1) }
        historyVersion++
    }

    /** Lo que dice el reproductor de verdad pasa a la pantalla (también lo que se toca en la notificación). */
    private fun sync(elapsedMs: Long) {
        val snap = engine.snapshot()

        val p = preview
        val ps = snap.preview
        if (p != null && ps != null) {
            previewPositionMs = ps.positionMs
            if (ps.durationMs > 0) previewDurationMs = ps.durationMs
            previewLoading = ps.loading
            if (!ps.loading) previewPlaying = ps.playing
            when {
                ps.error != null -> {
                    val r = previewResult
                    stopPreview()
                    showError("No se pudo escuchar", ps.error) { r?.let { togglePreview(it) } }
                }
                ps.ended -> stopPreview()
            }
        }

        if (queue.isEmpty || snap.index < 0) {
            if (isPlaying && preview == null) isPlaying = false
            return
        }
        // «Repetir» cambiado desde fuera de la app (el botón de la notificación). «No repetir» ya
        // no existe: si el reproductor lo tiene, se le vuelve a poner el de la app.
        val repeat = snap.repeat
        if (repeat == RepeatMode.Off) {
            engine.setRepeat(queue.repeat)
        } else if (repeat != null && repeat != queue.repeat) {
            queue = queue.copy(repeat = repeat)
            saveQueue()
        }
        if (snap.index != queue.index && snap.index < queue.items.size) {
            queue = queue.jumpTo(snap.index)
            durationMs = 0
            reaction++
            saveQueue()
        }
        // Aleatorio y sonando la última: se mezcla la vuelta siguiente sin cortar la que suena.
        if (queue.needsReshuffle) {
            queue = queue.reshuffled()
            engine.reorder(tracks(queue.items), queue.index)
            saveQueue()
        }
        if (snap.transitions != lastTransitions) {
            lastTransitions = snap.transitions
            resetCount()
            currentSong?.let(::ensureLyrics)
        }
        if (preview == null && isPlaying != snap.playing) {
            isPlaying = snap.playing
            reaction++
            saveQueue()
            val atTheEnd = snap.durationMs > 0 && snap.positionMs >= snap.durationMs - 1_500
            if (!snap.playing && sleepTimer?.endOfSong == true && (atTheEnd || snap.ended)) {
                // El motor se ha parado al final de la canción, como pedía el temporizador.
                sleepTimer = null
                engine.setPauseAtEndOfSong(false)
                toast("Buenas noches: el temporizador ha parado la música.", Mood.Dormido, Tone.Bone)
            }
        }
        val advanced = snap.positionMs - lastPosition
        if (snap.playing && advanced in 1..(elapsedMs + 1_500)) listenedMs += advanced
        lastPosition = snap.positionMs
        positionMs = snap.positionMs
        if (snap.durationMs > 0) durationMs = snap.durationMs
        val s = currentSong
        if (!counted && s != null && listenedMs >= minOf(30_000L, maxOf(1, currentDurationSec) * 500L)) {
            counted = true
            recordPlay(s)
        }
        if (snap.errorSerial != lastErrorSerial) {
            lastErrorSerial = snap.errorSerial
            snap.error?.let { showError("No se pudo reproducir", it, null) }
        }
        if (snap.playing) {
            sinceSaved += elapsedMs
            if (sinceSaved >= 5_000) saveQueue()
        }
    }

    private var sinceSaved = 0L

    /** Lo último que se ha guardado de la cola: solo se escribe lo que cambia. */
    private val savedQueue = HashMap<String, String>()

    /** La cola y por dónde iba, para seguir al volver a abrir la app. */
    private fun saveQueue() {
        sinceSaved = 0
        if (isDemo) return
        fun put(key: String, value: String) {
            if (savedQueue[key] == value) return
            savedQueue[key] = value
            store.setSetting(key, value)
        }
        put("queue.items", queue.items.joinToString("\n"))
        put("queue.original", queue.original.joinToString("\n"))
        put("queue.index", queue.index.toString())
        put("queue.shuffle", queue.shuffle.toString())
        put("queue.repeat", queue.repeat.name)
        put("queue.position", positionMs.toString())
        put("queue.source", queueSource)
    }

    private fun restoreQueue() {
        if (isDemo) {
            // Como en las capturas del diseño: «repetir todo» encendido.
            queue = PlayQueue.of(SampleData.playlists[0].songIds, "s5", repeat = RepeatMode.All)
            queueSource = "NOCHES DE NEÓN"
            positionMs = 84_000
        } else {
            fun ids(key: String) = store.setting(key)?.split("\n")?.filter { id -> song(id)?.let(::canPlay) == true }.orEmpty()
            val items = ids("queue.items")
            val savedIndex = store.setting("queue.index")?.toIntOrNull() ?: 0
            val savedItems = store.setting("queue.items")?.split("\n").orEmpty()
            val currentId = savedItems.getOrNull(savedIndex)
            val index = items.indexOf(currentId)
            if (items.isNotEmpty()) {
                queue = PlayQueue(
                    items = items,
                    index = if (index >= 0) index else 0,
                    shuffle = store.setting("queue.shuffle") == "true",
                    repeat = (RepeatMode.entries.firstOrNull { it.name == store.setting("queue.repeat") } ?: RepeatMode.All).effective,
                    original = ids("queue.original").ifEmpty { items },
                )
                queueSource = store.setting("queue.source").orEmpty()
                positionMs = if (index >= 0) store.setting("queue.position")?.toLongOrNull() ?: 0L else 0L
            } else {
                queue = PlayQueue(repeat = (RepeatMode.entries.firstOrNull { it.name == store.setting("queue.repeat") } ?: RepeatMode.All).effective)
            }
        }
        engine.setRepeat(queue.repeat)
        applyVolume()
        if (!queue.isEmpty) engine.load(tracks(queue.items), queue.index, positionMs, play = false)
    }

    // ---------- Letras ----------

    private val lyricsCache = HashMap<String, LyricsData?>()
    private val lyricsAsked = mutableSetOf<String>()

    /** Sube cuando llega una letra nueva (para que el reproductor la enseñe). */
    private var lyricsVersion by mutableIntStateOf(0)

    /** La letra guardada de una canción (null si no hay). */
    fun lyricsFor(songId: String): LyricsData? {
        lyricsVersion
        if (!lyricsCache.containsKey(songId)) {
            lyricsCache[songId] = if (isDemo) SampleData.lyricsData(songId) ?: store.lyrics(songId) else store.lyrics(songId)
        }
        return lyricsCache[songId]
    }

    private fun putLyrics(songId: String, lyrics: LyricsData?) {
        lyricsCache[songId] = lyrics
        lyricsVersion++
    }

    /** Si no tiene letra guardada, se pregunta a LRCLIB (como mucho una vez por semana y canción). */
    private fun ensureLyrics(song: Song) {
        if (isDemo || !online || song.id in lyricsAsked) return
        if (lyricsFor(song.id) != null) return
        val checked = store.lyricsCheckedAt(song.id)
        if (checked != null && nowMillis() - checked < 7 * Stats.DAY_MS) return
        lyricsAsked += song.id
        scope?.launch {
            val found = try {
                services.lyrics.find(song.title, song.artist, song.album, song.durationSec)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return@launch
            }
            store.saveLyrics(song.id, found, nowMillis())
            putLyrics(song.id, found)
        }
    }

    // ---------- Vista previa ----------

    var preview by mutableStateOf<Preview?>(null)
        private set
    var previewPlaying by mutableStateOf(false)
        private set

    /** Buscando el audio de la vista previa. */
    var previewLoading by mutableStateOf(false)
        private set
    var previewPositionMs by mutableLongStateOf(0L)
        private set
    var previewDurationMs by mutableLongStateOf(0L)
        private set
    private var previewResult: SearchResult? = null

    /** El resultado que suena en la vista previa (para añadirlo desde el reproductor). */
    val previewTarget: SearchResult? get() = previewResult.takeIf { preview != null }
    private var previewJob: Job? = null
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
            if (isPlaying) {
                engine.pause()
                isPlaying = false
            }
        } else {
            previewJob?.cancel()
            engine.stopPreview()
        }
        preview = Preview(r.id, r.title, r.artist, r.durationSec, r.coverColor, r.cover)
        previewResult = r
        previewPositionMs = 0
        previewDurationMs = r.durationSec * 1000L
        previewPlaying = true
        previewLoading = true
        reaction++
        val s = scope
        if (s == null) {
            previewLoading = false
            return
        }
        previewJob = s.launch {
            try {
                val source = services.source.stream(r)
                if (preview?.id == r.id) {
                    engine.startPreview(source)
                    if (!previewPlaying) engine.previewPause()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (preview?.id == r.id) {
                    stopPreview()
                    showError("No se pudo escuchar", e.failureKind().explain()) { togglePreview(r) }
                }
            }
        }
    }

    fun stopPreview() {
        if (preview == null) return
        previewJob?.cancel()
        engine.stopPreview()
        // Si el reproductor enseñaba la vista previa, se cierra con ella.
        playerOpen = false
        preview = null
        previewResult = null
        previewPlaying = false
        previewLoading = false
        if (resumeAfterPreview && currentSong != null) {
            engine.play()
            isPlaying = true
        }
        resumeAfterPreview = false
        reaction++
    }

    private fun dropPreview() {
        if (preview == null) return
        previewJob?.cancel()
        engine.stopPreview()
        preview = null
        previewResult = null
        previewPlaying = false
        previewLoading = false
        resumeAfterPreview = false
    }

    // ---------- Buscar ----------

    var query by mutableStateOf(if (isDemo) "luz de farola" else "")
    var searchTab by mutableStateOf(SearchTab.Songs)

    var songResults by mutableStateOf(TabResults<SearchResult>())
        private set
    var videoResults by mutableStateOf(TabResults<SearchResult>())
        private set
    var albumResults by mutableStateOf(TabResults<AlbumResult>())
        private set
    var playlistResults by mutableStateOf(TabResults<PlaylistResult>())
        private set

    /** Si lo escrito en el buscador es el enlace de una lista de YouTube, su dirección (no se busca). */
    val linkedList: String? get() = YouTubeLinks.playlistUrl(query)

    /** Sube al pedir «Reintentar» en el buscador. */
    private var searchRetry by mutableIntStateOf(0)

    fun retrySearch() {
        searchRetry++
    }

    /** Busca [q] en la pestaña [tab]. internal: las capturas de pantalla la llaman sin el bucle. */
    internal suspend fun search(q: String, tab: SearchTab) {
        val current = when (tab) {
            SearchTab.Songs -> songResults.query
            SearchTab.Videos -> videoResults.query
            SearchTab.Albums -> albumResults.query
            SearchTab.Playlists -> playlistResults.query
        }
        val failed = when (tab) {
            SearchTab.Songs -> songResults.error
            SearchTab.Videos -> videoResults.error
            SearchTab.Albums -> albumResults.error
            SearchTab.Playlists -> playlistResults.error
        }
        val busy = when (tab) {
            SearchTab.Songs -> songResults.loading
            SearchTab.Videos -> videoResults.loading
            SearchTab.Albums -> albumResults.loading
            SearchTab.Playlists -> playlistResults.loading
        }
        if (current == q && failed == null && !busy) return
        when (tab) {
            SearchTab.Songs -> songResults = songResults.copy(query = q, loading = true, error = null)
            SearchTab.Videos -> videoResults = videoResults.copy(query = q, loading = true, error = null)
            SearchTab.Albums -> albumResults = albumResults.copy(query = q, loading = true, error = null)
            SearchTab.Playlists -> playlistResults = playlistResults.copy(query = q, loading = true, error = null)
        }
        try {
            when (tab) {
                SearchTab.Songs -> songResults = TabResults(q, services.source.searchSongs(q))
                SearchTab.Videos -> videoResults = TabResults(q, services.source.searchVideos(q))
                SearchTab.Albums -> albumResults = TabResults(q, services.source.searchAlbums(q))
                SearchTab.Playlists -> playlistResults = TabResults(q, services.source.searchPlaylists(q))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val kind = e.failureKind()
            when (tab) {
                SearchTab.Songs -> songResults = songResults.copy(loading = false, error = kind)
                SearchTab.Videos -> videoResults = videoResults.copy(loading = false, error = kind)
                SearchTab.Albums -> albumResults = albumResults.copy(loading = false, error = kind)
                SearchTab.Playlists -> playlistResults = playlistResults.copy(loading = false, error = kind)
            }
        }
    }

    /** Canciones de cada álbum ya consultado (para «12 CANCIONES» y saber si ya lo tienes). */
    val albumTracks = mutableStateMapOf<String, List<SearchResult>>()
    private val albumAsked = mutableSetOf<String>()
    private val albumPermits = Semaphore(3)

    /** Álbumes cuyas canciones no se han podido pedir, y por qué (para el aviso dentro del álbum). */
    val albumFailed = mutableStateMapOf<String, FailureKind>()

    /** Pide (una vez) las canciones de un álbum que se ve en la lista. */
    fun loadAlbumTracks(album: AlbumResult) {
        if (album.key in albumTracks || album.key in albumAsked || !online) return
        albumAsked += album.key
        albumFailed -= album.key
        scope?.launch {
            try {
                val list = albumPermits.withPermit { services.source.albumTracks(album) }
                albumTracks[album.key] = list
            } catch (e: CancellationException) {
                albumAsked -= album.key
                throw e
            } catch (e: Throwable) {
                albumAsked -= album.key
                albumFailed[album.key] = e.failureKind()
            }
        }
    }

    /** El álbum abierto (o el último que se abrió). */
    var openedAlbum by mutableStateOf<AlbumResult?>(null)
        private set

    /** Tocar un álbum en Buscar: se abre con sus canciones, para elegir una a una. */
    fun openAlbum(album: AlbumResult) {
        openedAlbum = album
        loadAlbumTracks(album)
        go(Screen.AlbumDetail(album.key))
    }

    /** «REINTENTAR» dentro de un álbum que no se pudo abrir. */
    fun retryAlbum() {
        openedAlbum?.let(::loadAlbumTracks)
    }

    private suspend fun tracksOf(album: AlbumResult): List<SearchResult> =
        albumTracks[album.key] ?: services.source.albumTracks(album).also { albumTracks[album.key] = it }

    // ---------- Listas de otros ----------

    /** La lista de otra persona abierta (o la última que se abrió). */
    var otherList by mutableStateOf<OtherListState?>(null)
        private set

    /**
     * Abre una lista de otra persona (de la pestaña Listas o de un enlace pegado) para elegir
     * canciones sueltas. [hint] son los datos que ya se conocen, para enseñarlos mientras carga.
     */
    fun openOtherList(url: String, hint: PlaylistResult? = null) {
        val open = otherList
        if (open == null || open.url != url || (open.error != null && !open.loading)) {
            val st = OtherListState(url, hint)
            otherList = st
            scope?.launch { loadOtherList(st) }
        }
        go(Screen.OtherListDetail(url))
    }

    /** «REINTENTAR» en una lista que no se pudo abrir. */
    fun retryOtherList() {
        val st = otherList ?: return
        if (st.loading) return
        scope?.launch { loadOtherList(st) }
    }

    internal suspend fun loadOtherList(st: OtherListState) {
        st.loading = true
        st.error = null
        try {
            st.list = services.source.otherList(st.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            st.error = e.failureKind()
        } finally {
            st.loading = false
        }
    }

    /** La descarga de «+ AÑADIR TODAS» de una lista (para su barra). */
    fun otherListJob(url: String): DownloadJob? = downloads.firstOrNull { it.isCollection && it.albumKey == "list:$url" }

    private fun addWholeList(list: OtherList, playlistId: String?) {
        val key = "list:${list.info.url}"
        if (otherListJob(list.info.url) != null) return
        val have = list.tracks.mapNotNull { librarySongFor(it) }
        if (playlistId != null && have.isNotEmpty()) addToPlaylist(playlistId, have.map { it.id })
        val missing = list.tracks.filter { librarySongFor(it) == null && downloadFraction(it.id) == null }.distinctBy { it.id }
        if (missing.isEmpty()) {
            val p = playlist(playlistId)
            toast(if (p != null) "Añadidas a «${p.name}»: ya las tenías todas." else "Ya tienes todas las de «${list.info.title}».", Mood.Uwu, Tone.Jade)
            return
        }
        missing.forEach { if (it.id !in addedIds) addedIds.add(it.id) }
        enqueue(
            DownloadJob(newId(), list.info.title, playlistId, isCollection = true, albumKey = key).apply {
                items.addAll(missing.map { requestFor(it) })
            },
        )
    }

    // ---------- Añadir y descargar ----------

    /** Resultados añadidos en esta sesión (su «+» pasa a ✓). */
    val addedIds = mutableStateListOf<String>()
    val downloads = mutableStateListOf<DownloadJob>()
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

    private fun requestFor(r: SearchResult, title: String? = null, artist: String? = null, album: String? = null): DownloadRequest {
        val s = songFor(r, title, artist)
        return DownloadRequest(
            r,
            s.title,
            s.artist,
            album ?: r.album,
            quality = settings.downloadQuality,
            // La carpeta solo cuenta donde se puede elegir (el PC), aunque el ajuste llegue de otro sitio.
            folder = settings.downloadFolder.takeIf { canChooseFolder },
        )
    }

    /** Progreso de descarga de un resultado (0–1), o null si no se está descargando. */
    fun downloadFraction(resultId: String): Float? {
        val first = downloads.firstOrNull()
        for (job in downloads) {
            val i = job.items.indexOfFirst { it.result.id == resultId }
            if (i >= 0 && i >= job.done) return if (job == first && i == job.done) job.itemProgress else 0f
        }
        return null
    }

    fun albumJob(album: AlbumResult): DownloadJob? = downloads.firstOrNull { it.isCollection && it.albumKey == album.key }

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
                if (downloadFraction(r.id) != null) return
                if (r.id !in addedIds) addedIds.add(r.id)
                val request = requestFor(r, title, artist)
                enqueue(DownloadJob(newId(), request.title, playlistId, isCollection = false).apply { items.add(request) })
            }
            is AddTarget.Album -> addAlbum(target.album, playlistId)
            is AddTarget.WholeList -> addWholeList(target.list, playlistId)
        }
    }

    private fun addAlbum(album: AlbumResult, playlistId: String?) {
        if (albumJob(album) != null) return
        val job = DownloadJob(newId(), album.title, playlistId, isCollection = true, albumKey = album.key).apply { resolving = true }
        enqueue(job)
        val s = scope ?: return
        s.launch {
            val list = try {
                tracksOf(album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                downloads.remove(job)
                showError("No se pudo abrir «${album.title}»", e.failureKind().explain()) { addAlbum(album, playlistId) }
                return@launch
            }
            val have = list.mapNotNull { librarySongFor(it) }
            if (playlistId != null && have.isNotEmpty()) addToPlaylist(playlistId, have.map { it.id })
            val missing = list.filter { librarySongFor(it) == null && downloadFraction(it.id) == null }.distinctBy { it.id }
            if (missing.isEmpty()) {
                downloads.remove(job)
                toast(if (list.isEmpty()) "«${album.title}» no tiene canciones." else "Ya tienes todo «${album.title}».", Mood.Uwu, Tone.Jade)
                return@launch
            }
            missing.forEach { if (it.id !in addedIds) addedIds.add(it.id) }
            job.items.addAll(missing.map { requestFor(it, album = it.album ?: album.title) })
            job.resolving = false
        }
    }

    private fun enqueue(job: DownloadJob) {
        downloads.add(job)
        if (worker?.isActive != true) worker = scope?.launch { work() }
    }

    private suspend fun work() {
        while (downloads.isNotEmpty()) {
            val job = downloads.first()
            while (job.resolving && job in downloads) delay(100)
            if (job !in downloads) continue
            runJob(job)
            downloads.remove(job)
        }
    }

    /** Descarga las canciones de un trabajo una a una. Lo que falla se puede reintentar al final. */
    private suspend fun runJob(job: DownloadJob) {
        val failed = mutableListOf<Pair<DownloadRequest, FailureKind>>()
        while (job.done < job.total) {
            while (!online) delay(300)
            val item = job.items[job.done]
            var attempt = 0
            while (true) {
                try {
                    val got = services.downloader.download(item) { p -> job.itemProgress = p.coerceIn(0f, 1f) }
                    addDownloaded(got, job.playlistId)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    attempt++
                    val kind = e.failureKind()
                    val worthRetrying = kind == FailureKind.Network || kind == FailureKind.Changed || kind == FailureKind.Unknown
                    if (!isDemo && worthRetrying && attempt < 3) {
                        job.itemProgress = 0f
                        delay(2_000L * attempt)
                        while (!online) delay(300)
                        continue
                    }
                    failed += item to kind
                    job.failed++
                    addedIds.remove(item.result.id)
                    break
                }
            }
            job.itemProgress = 0f
            job.done++
        }
        finished(job, failed)
    }

    private fun addDownloaded(got: DownloadedSong, playlistId: String?) {
        val song = got.song.let { if (it.addedAt == 0L) it.copy(addedAt = nowMillis()) else it }
        val i = songs.indexOfFirst { it.id == song.id }
        val kept = if (i >= 0) song.copy(score = songs[i].score, plays = songs[i].plays) else song
        if (i >= 0) songs[i] = kept else songs.add(kept)
        store.saveSong(kept)
        if (!isDemo) {
            store.saveLyrics(kept.id, got.lyrics, nowMillis())
            putLyrics(kept.id, got.lyrics)
        }
        playlistId?.let { addToPlaylist(it, listOf(kept.id)) }
    }

    private fun finished(job: DownloadJob, failed: List<Pair<DownloadRequest, FailureKind>>) {
        val ok = job.total - failed.size
        if (ok > 0) {
            val p = playlist(job.playlistId)
            val text = when {
                !job.isCollection && p != null -> "«${job.title}» ya está en tu biblioteca y en «${p.name}», con carátula y letra."
                !job.isCollection -> "«${job.title}» ya está en tu biblioteca, con carátula y letra."
                ok == 1 -> "«${job.title}»: 1 canción nueva en tu biblioteca."
                else -> "«${job.title}»: $ok canciones nuevas en tu biblioteca."
            }
            toast(text, Mood.Owo)
        }
        if (failed.isNotEmpty()) {
            val title = if (failed.size == 1) "No se pudo descargar «${failed[0].first.title}»" else "No se pudieron descargar ${failed.size} canciones"
            showError(title, failed[0].second.explain()) {
                failed.forEach { (r, _) -> if (r.result.id !in addedIds) addedIds.add(r.result.id) }
                enqueue(
                    DownloadJob(newId(), job.title, job.playlistId, job.isCollection && failed.size > 1, job.albumKey).apply {
                        items.addAll(failed.map { it.first })
                    },
                )
            }
        }
    }

    // ---------- Carpeta de descargas ----------

    /** Ajustes → «Carpeta de descargas»: abre el selector de carpetas (solo en el PC). */
    fun chooseDownloadFolder() {
        folderPicker?.invoke()
    }

    /** La carpeta elegida, o null para volver a la de la app. Lo que ya está descargado no se mueve. */
    fun setDownloadFolder(path: String?) {
        val before = settings.downloadFolder
        settings.downloadFolder = path
        val now = settings.downloadFolder
        if (now == before) return
        toast(
            if (now == null) "Las descargas nuevas vuelven a la carpeta de la app." else "Las descargas nuevas irán a «${folderName(now)}».",
            Mood.Owo,
        )
    }

    // ---------- Archivos propios ----------

    /** Abre el selector de archivos del sistema para añadir música propia. */
    fun importFiles() {
        val open = fileImporter
        if (open == null) soon("Añadir tus propios archivos aquí") else open()
    }

    /** Lo que la plataforma ya ha copiado y leído de los archivos elegidos. */
    fun addImported(list: List<DownloadedSong>, failedCount: Int = 0) {
        list.forEach { addDownloaded(it, null) }
        when {
            list.isEmpty() && failedCount > 0 -> showError(
                "No se pudieron añadir los archivos",
                "Esos archivos no parecen de audio o no se pueden leer.",
                retry = fileImporter,
            )
            list.size == 1 -> toast("«${list[0].song.title}» ya está en tu biblioteca.", Mood.Owo)
            list.isNotEmpty() -> toast("${list.size} canciones nuevas en tu biblioteca.", Mood.Owo)
        }
        if (list.isNotEmpty() && failedCount > 0) {
            showError("Algunos archivos no se pudieron añadir", "$failedCount no parecen de audio o no se pueden leer.", null)
        }
    }

    // ---------- Playlists ----------

    private fun savePlaylistAt(i: Int) {
        store.savePlaylist(playlists[i], i)
    }

    fun addToPlaylist(id: String, songIds: List<String>) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = playlists[i]
        val added = songIds.filter { it !in p.songIds }.distinct()
        if (added.isEmpty()) return
        val now = nowMillis()
        val updated = p.copy(songIds = p.songIds + added, addedAt = p.addedAt + added.associateWith { now })
        playlists[i] = updated
        savePlaylistAt(i)
    }

    fun createPlaylist(name: String): String {
        val id = "p${nowMillis()}-${newId()}"
        var unique = name
        var n = 2
        while (playlists.any { it.name.equals(unique, ignoreCase = true) }) unique = "$name ($n)".also { n++ }
        val colors = CoverPalette.colors
        playlists.add(Playlist(id, unique, emptyList(), colors[playlists.size % colors.size]))
        savePlaylistAt(playlists.lastIndex)
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
            Prompt.NewRanking, is Prompt.NewRankingWith -> {
                prompt = null
                val id = "r${nowMillis()}-${newId()}"
                val first = (p as? Prompt.NewRankingWith)?.songId
                rankings.add(CustomRanking(id, name, listOfNotNull(first)))
                store.saveRanking(rankings.last(), rankings.lastIndex)
                openRankingId = id
                toast(if (first != null) "Ranking «$name» creado, con «${song(first)?.title}»." else "Ranking «$name» creado.", Mood.Feliz)
            }
            is Prompt.EditSong, null -> Unit
        }
    }

    /** Guarda el título y el artista corregidos de una canción. */
    fun editSong(songId: String, title: String, artist: String) {
        val t = title.trim()
        val a = artist.trim()
        if (t.isEmpty()) return
        prompt = null
        val before = song(songId) ?: return
        if (before.title == t && before.artist == a.ifEmpty { before.artist }) return
        val after = before.copy(title = t, artist = a.ifEmpty { before.artist })
        replaceSong(songId) { after }
        store.saveSong(after)
        toast("Guardado: «${after.title}» de ${after.artist}.", Mood.Feliz)
    }

    // ---------- Ranking ----------

    var rankTab by mutableStateOf(RankTab.General)

    /** Puntuadas desde que se entró en Ranking (fila en jade). Se limpia al salir. */
    val freshIds = mutableStateListOf<String>()
    var editingScoreId by mutableStateOf<String?>(null)
    var openRankingId by mutableStateOf<String?>(rankings.firstOrNull()?.id)

    /** Pone o cambia una nota. Sale un aviso con «DESHACER» unos segundos. */
    fun rate(songId: String, score: Int) {
        val i = songs.indexOfFirst { it.id == songId }
        if (i < 0) return
        val before = songs[i].score
        editingScoreId = null
        if (before == score) return
        songs[i] = songs[i].copy(score = score)
        store.setScore(songId, score)
        historyVersion++
        if (songId !in freshIds) freshIds.add(songId)
        val title = songs[i].title
        toast("«$title»: ${starsText(score)}", Mood.Feliz, Tone.Jade, actionLabel = "DESHACER") {
            replaceSong(songId) { it.copy(score = before) }
            store.setScore(songId, before)
            historyVersion++
            if (before == null) freshIds.remove(songId)
        }
    }

    fun moveInRanking(rankingId: String, from: Int, to: Int) {
        val i = rankings.indexOfFirst { it.id == rankingId }
        if (i < 0) return
        val moved = Ranking.move(rankings[i], from, to)
        if (moved == rankings[i]) return
        rankings[i] = moved
        store.saveRanking(moved, i)
    }

    /** Añade una canción a un ranking personal (al final). */
    fun addToRanking(rankingId: String, songId: String) {
        sheet = null
        val i = rankings.indexOfFirst { it.id == rankingId }
        if (i < 0) return
        val r = rankings[i]
        if (songId in r.songIds) {
            toast("Ya está en «${r.name}».", Mood.Uwu, Tone.Jade)
            return
        }
        rankings[i] = r.copy(songIds = r.songIds + songId)
        store.saveRanking(rankings[i], i)
        toast("Añadida a «${r.name}», en el puesto ${r.songIds.size + 1}.", Mood.Feliz)
    }

    // ---------- Otras pantallas ----------

    /** Estadísticas: últimos 30 días, por meses o desde siempre; y el mes elegido (null = el último). */
    var statsTab by mutableStateOf(StatsTab.Recent)
    var statsMonth by mutableStateOf<YearMonth?>(null)
    var panelFilter by mutableStateOf("")

    // ---------- Wrapped ----------

    /** El periodo del Wrapped (null = el último mes con escuchas). */
    var wrappedPeriod by mutableStateOf<WrappedPeriod?>(null)

    /** Los periodos con escuchas: cada año seguido de sus meses, de lo más reciente a lo más antiguo. */
    fun wrappedPeriods(): List<WrappedPeriod> = Wrapped.periods(playLog, ::utcOffsetMillis)

    /** El resumen de [period] (null si no sonó nada). */
    fun wrappedReport(period: WrappedPeriod): WrappedReport? = Wrapped.report(songs, playLog, period, ::utcOffsetMillis)

    /** Abre el Wrapped (en el último mes con escuchas). La primera vez gana el fantasma «Fiesta». */
    fun openWrapped() {
        wrappedPeriod = null
        settings.wrappedSeen = true
        go(Screen.Wrapped)
    }

    // ---------- Fantasmas coleccionables ----------

    /** Lo que llevas para ganar cada fantasma. */
    fun ghostFacts(): GhostFacts = GhostCollection.facts(songs, playLog, settings.wrappedSeen, ::utcOffsetMillis)

    /** Los fantasmas que tienes: los ganados ahora y los que ya ganaste (no se pierden). */
    fun ownedGhosts(facts: GhostFacts = ghostFacts()): Set<GhostSkin> =
        GhostCollection.unlocked(facts) + (settings.ghostsAnnounced ?: emptySet())

    /** Ponerse un fantasma (solo uno que se tenga). */
    fun chooseGhost(skin: GhostSkin) {
        if (skin !in ownedGhosts()) return
        if (settings.ghostSkin == skin) return
        settings.ghostSkin = skin
        toast("Ahora eres el fantasma ${skin.label}", Mood.Owo, Tone.Neon)
    }

    private var ghostKey: Triple<Int, Int, Boolean>? = null
    private val ghostsToAnnounce = ArrayDeque<GhostSkin>()

    /**
     * Mira si se ha ganado algún fantasma (solo cuando cambia el historial, la biblioteca o el
     * Wrapped) y lo anuncia cuando no haya otro aviso en pantalla, con «VER» a la colección.
     */
    private fun checkGhosts() {
        if (isDemo) return
        val key = Triple(historyVersion, songs.size, settings.wrappedSeen)
        if (key != ghostKey) {
            ghostKey = key
            val won = GhostCollection.unlocked(ghostFacts())
            val told = settings.ghostsAnnounced
            if (told == null) {
                // La primera vez (o al actualizar desde una versión sin fantasmas): sin avisos.
                settings.ghostsAnnounced = won
            } else {
                (won - told).filter { it !in ghostsToAnnounce }.forEach { ghostsToAnnounce.addLast(it) }
            }
        }
        if (ghostsToAnnounce.isEmpty() || toast != null) return
        val skin = ghostsToAnnounce.removeFirst()
        settings.ghostsAnnounced = (settings.ghostsAnnounced ?: emptySet()) + skin
        toast("¡Nuevo fantasma: ${skin.label}!", Mood.Contento, Tone.Neon, actionLabel = "VER", millis = 5_000) { go(Screen.Ghosts) }
    }
    val spotify = SpotifyImportState()

    /**
     * «Pegar playlist de Spotify» / «Importar de Spotify»: valida el enlace, lee la playlist de su
     * página pública (sin cuenta ni clave), busca cada canción en YouTube Music y crea la playlist
     * con lo seguro; lo dudoso queda para confirmarlo.
     */
    fun startSpotifyImport() {
        val sp = spotify
        if (sp.phase == ImportPhase.Matching) return
        when (val link = Spotify.parse(sp.link)) {
            SpotifyLink.Invalid -> sp.error = "Eso no parece un enlace de playlist de Spotify. En Spotify: Compartir → Copiar enlace."
            SpotifyLink.Editorial -> sp.error = SPOTIFY_EDITORIAL
            is SpotifyLink.Playlist -> {
                if (!online) {
                    sp.error = "Sin conexión: importar vuelve cuando haya internet."
                    return
                }
                val reader = services.spotify
                if (reader == null) {
                    sp.error = "Importar de Spotify no está disponible aquí."
                    return
                }
                val s = scope ?: return
                sp.error = null
                sp.note = null
                sp.checked = 0
                sp.total = 0
                sp.phase = ImportPhase.Matching
                s.launch { importSpotify(reader, link.id) }
            }
        }
    }

    private val spotifyPermits = Semaphore(3)

    private suspend fun importSpotify(reader: SpotifyReader, id: String) {
        val sp = spotify
        fun fail(text: String) {
            sp.error = text
            sp.phase = ImportPhase.Paste
        }
        val list = try {
            reader.playlist(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(spotifyProblem(e.failureKind()))
            return
        }
        if (list.editorial) return fail(SPOTIFY_EDITORIAL)
        if (list.tracks.isEmpty()) return fail("Esa playlist está vacía, o Spotify no enseña sus canciones.")
        sp.total = list.tracks.size
        // Cada canción se busca en YouTube Music (de tres en tres) y se empareja con lo encontrado.
        val found = arrayOfNulls<TrackMatch>(list.tracks.size)
        var failed = 0
        var problem = FailureKind.Unknown
        coroutineScope {
            list.tracks.forEachIndexed { i, track ->
                launch {
                    val results = spotifyPermits.withPermit {
                        try {
                            services.source.searchSongs(Spotify.query(track))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed++
                            problem = e.failureKind()
                            emptyList()
                        }
                    }
                    found[i] = Spotify.match(track, results)
                    sp.checked++
                }
            }
        }
        if (failed == list.tracks.size) return fail("No se ha podido buscar en YouTube Music. " + problem.explain())
        val matches = found.mapIndexed { i, m -> m ?: TrackMatch(list.tracks[i], MatchKind.SinCoincidencia, null) }
        sp.matches.clear()
        sp.matches.addAll(matches)
        sp.decisions.clear()
        val pid = createPlaylist(list.name.trim().ifEmpty { "De Spotify" })
        sp.playlistId = pid
        acceptMatches(pid, matches.filter { it.kind == MatchKind.Segura }.mapNotNull { it.result })
        sp.note = listOfNotNull(
            if (list.total > list.tracks.size) {
                "Spotify solo enseña ${list.tracks.size} de las ${list.total} canciones sin iniciar sesión: se han buscado esas."
            } else {
                null
            },
            when (failed) {
                0 -> null
                1 -> "Una canción no se ha podido buscar: está en «Sin coincidencia» para buscarla a mano."
                else -> "$failed canciones no se han podido buscar: están en «Sin coincidencia» para buscarlas a mano."
            },
        ).joinToString(" ").ifEmpty { null }
        sp.phase = ImportPhase.Done
    }

    /** Por qué no se ha podido leer la playlist de Spotify, dicho claro. */
    private fun spotifyProblem(kind: FailureKind): String = when (kind) {
        FailureKind.Network -> "No hay conexión con Spotify. Comprueba la red y reintenta."
        FailureKind.Unavailable ->
            "Spotify no enseña esa playlist: puede que sea privada o que ya no exista. Hazla pública en Spotify y vuelve a probar."
        FailureKind.Blocked -> "Spotify no deja leer la playlist sin iniciar sesión ahora mismo. Prueba dentro de un rato."
        FailureKind.Changed ->
            "Spotify ha cambiado su página y no se pueden leer las canciones. Hará falta una versión nueva de la app."
        else -> "Spotify no responde. Prueba dentro de un rato."
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
        enqueue(
            DownloadJob(newId(), playlist(pid)?.name ?: "", pid, isCollection = true).apply {
                items.addAll(missing.map { requestFor(it) })
            },
        )
    }

    fun setDemoEmpty(on: Boolean) {
        settings.demoEmpty = on
        if (on) {
            engine.pause()
            isPlaying = false
            dropPreview()
        }
    }

    // ---------- Actualizaciones ----------

    /** Versión de esta app («0.4.0»; «dev» al compilarla en el ordenador). La pone cada plataforma. */
    var appVersion by mutableStateOf("dev")

    /** Versión publicada más nueva que la instalada, si la hay. */
    var update by mutableStateOf<AppRelease?>(null)
        private set

    /** Progreso de la descarga de [update] (0–1), o null si no se está descargando. */
    var updateProgress by mutableStateOf<Float?>(null)
        private set

    var checkingUpdates by mutableStateOf(false)
        private set

    /** «LUEGO»: esa versión ya no vuelve a salir sola (sí al buscar a mano en Ajustes). */
    private var dismissedUpdate by mutableStateOf(store.setting("update.dismissed"))

    /** El aviso de versión nueva abajo: la hay, no se ha dejado para luego, o se está descargando. */
    val showUpdateNotice: Boolean
        get() = updateProgress != null || update.let { it != null && it.version != dismissedUpdate }

    val canUpdate: Boolean get() = services.updates != null && !isDemo

    /** Busca una versión nueva. [manual] = desde Ajustes: dice el resultado aunque no haya nada. */
    fun checkForUpdates(manual: Boolean = true) {
        val s = scope ?: return
        s.launch { findUpdate(manual) }
    }

    internal suspend fun findUpdate(manual: Boolean) {
        val updates = services.updates ?: return
        if (isDemo || checkingUpdates) return
        checkingUpdates = true
        try {
            val latest = updates.latest()
            val newer = latest?.takeIf { AppVersions.isNewer(it.version, appVersion) }
            update = newer
            if (manual) {
                if (newer != null) dismissedUpdate = null
                else toast("Ya tienes la última versión (${appVersion}).", Mood.Uwu, Tone.Jade)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (manual) showError("No se pudo buscar si hay versión nueva", e.failureKind().explain()) { checkForUpdates() }
        } finally {
            checkingUpdates = false
        }
    }

    /** Al abrir la app y cada pocas horas, si hay red. */
    private suspend fun watchUpdates() {
        if (services.updates == null || isDemo) return
        delay(4_000)
        while (true) {
            while (!online) delay(1_000)
            findUpdate(manual = false)
            delay(UPDATE_CHECK_EVERY_MS)
        }
    }

    fun dismissUpdate() {
        val v = update?.version ?: return
        dismissedUpdate = v
        store.setSetting("update.dismissed", v)
    }

    /** «ACTUALIZAR»: descarga el instalador y lo abre (en Android pide confirmación al sistema). */
    fun installUpdate() {
        val release = update ?: return
        val updates = services.updates ?: return
        val s = scope ?: return
        if (updateProgress != null) return
        updateProgress = 0f
        s.launch {
            val path = try {
                updates.download(release) { p -> updateProgress = p.coerceIn(0f, 1f) }
            } catch (e: CancellationException) {
                updateProgress = null
                throw e
            } catch (e: Throwable) {
                updateProgress = null
                showError("No se pudo descargar la versión ${release.version}", e.failureKind().explain()) { installUpdate() }
                return@launch
            }
            updateProgress = null
            val problem = updates.install(path)
            if (problem != null) showError("No se pudo instalar la versión ${release.version}", problem) { installUpdate() }
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

    // ---------- Sincronizar ----------

    /** «Vincular con el PC / con el móvil»: abre la pantalla y arranca Syncthing. */
    fun openSync() {
        if (services.sync == null) {
            soon("Vincular con un QR")
            return
        }
        go(Screen.Sync)
        if (sync.phase == SyncPhase.Off || sync.phase == SyncPhase.Failed) startSync()
    }

    fun startSync() {
        val backend = services.sync ?: return
        val s = scope ?: return
        if (sync.phase == SyncPhase.Starting || sync.phase == SyncPhase.Pairing) return
        sync.phase = SyncPhase.Starting
        sync.error = null
        s.launch {
            try {
                val id = backend.start()
                sync.myId = id
                if (backend.showsQr) sync.qr = backend.qr(id)
                val peer = store.setting(SYNC_PEER)
                sync.peer = peer
                if (peer != null) {
                    watchSync(backend, peer)
                } else {
                    sync.phase = SyncPhase.Waiting
                    if (backend.showsQr) awaitPeer(backend)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncUnavailableException) {
                sync.phase = SyncPhase.Failed
                sync.error = "Esta instalación no trae Syncthing, así que aquí no se puede sincronizar."
            } catch (e: Exception) {
                sync.phase = SyncPhase.Failed
                sync.error = "No se ha podido arrancar la sincronización. Vuelve a probar dentro de un rato."
            }
        }
    }

    /**
     * Android ha parado el servicio de la sincronización y no deja volver a ponerlo en primer plano
     * (en Android 15 o más, el de «sincronización de datos» tiene 6 horas al día con la app en
     * segundo plano). Se para Syncthing y se avisa. No vuelve a arrancar sola, porque fallaría
     * otra vez: sigue con «REINTENTAR» o «Vincular», con la app abierta.
     */
    fun syncStoppedBySystem() {
        syncWatch?.cancel()
        syncWatch = null
        services.sync?.let { runCatching { it.stop() } }
        sync.connected = false
        sync.phase = SyncPhase.Failed
        sync.error = SYNC_STOPPED_BY_ANDROID
        toast("Sincronización en pausa: Android no la deja seguir en segundo plano.", Mood.Dormido, Tone.Bone)
    }

    /** Abre el escáner del móvil para leer el QR del PC. */
    fun scanSyncCode() {
        val scan = qrScanner ?: return
        scan { text -> if (text != null) pairWith(text) }
    }

    /** Vincula el dispositivo del código leído (o escrito a mano). */
    fun pairWith(text: String) {
        val backend = services.sync ?: return
        val id = SyncIds.device(text)
        when {
            id == null -> sync.error = "Ese código no es el de FreedomPlayer en el PC. Escanea el QR de «Vincular con el móvil»."
            id == sync.myId -> sync.error = "Ese es el código de este mismo dispositivo."
            else -> pairDevice(backend, id)
        }
    }

    private fun pairDevice(backend: SyncBackend, id: String) {
        val s = scope ?: return
        sync.error = null
        sync.phase = SyncPhase.Pairing
        s.launch {
            try {
                backend.pair(id, if (backend.showsQr) "Móvil" else "PC")
                store.setSetting(SYNC_PEER, id)
                sync.peer = id
                watchSync(backend, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sync.phase = SyncPhase.Failed
                sync.error = "No se ha podido vincular. Comprueba que el PC tiene abierto «Vincular con el móvil» y que los dos estáis en la misma red."
            }
        }
    }

    /**
     * El PC, con su QR a la vista: cuando el móvil lo escanea, intenta conectar; mientras esta
     * pantalla espera, se acepta a ese móvil (y solo entonces).
     */
    private fun awaitPeer(backend: SyncBackend) {
        syncWatch?.cancel()
        syncWatch = scope?.launch {
            while (isActive && sync.phase == SyncPhase.Waiting) {
                val asking = runCatching { backend.pendingDevice() }.getOrNull()
                if (asking != null && asking != sync.myId) {
                    pairDevice(backend, asking)
                    break
                }
                delay(2_000)
            }
        }
    }

    /**
     * Cada 2 s, cuánto lleva («12 de 20»), mientras la app esté abierta; y cada 20 s se
     * intercambia la biblioteca (playlists, notas, rankings e historial) con el otro dispositivo.
     */
    private fun watchSync(backend: SyncBackend, peer: String) {
        syncWatch?.cancel()
        sync.phase = SyncPhase.Syncing
        sync.connected = false
        val notFound = syncNotFound(backend)
        syncWatch = scope?.launch {
            var tick = 0
            var lastSeen = TimeSource.Monotonic.markNow()
            while (isActive) {
                val count = runCatching { backend.progress(peer) }.getOrNull()
                if (count != null) {
                    lastSeen = TimeSource.Monotonic.markNow()
                    sync.connected = true
                    if (sync.error == notFound) sync.error = null
                    sync.done = count.done
                    sync.total = count.total
                    sync.phase = if (count.done >= count.total) SyncPhase.Synced else SyncPhase.Syncing
                } else {
                    // Sin el otro dispositivo (apagado, sin FreedomPlayer abierto u otra red): se sigue
                    // esperando, y pasado un rato se avisa de qué comprobar.
                    sync.connected = false
                    if (isActive && lastSeen.elapsedNow().inWholeMilliseconds >= syncConnectTimeoutMs) sync.error = notFound
                }
                if (tick % 10 == 0) {
                    val local = LibraryData(songs.toList(), playlists.toList(), rankings.toList(), playLog.toList())
                    runCatching { backend.exchange(local) }.getOrNull()?.let { applySync(it) }
                }
                tick++
                delay(2_000)
            }
        }
    }

    /** El aviso de cuando, ya vinculados, el otro dispositivo no aparece. */
    private fun syncNotFound(backend: SyncBackend): String =
        if (backend.showsQr) {
            "No se encuentra el móvil. Comprueba que tiene FreedomPlayer abierto y que los dos estáis en la misma wifi."
        } else {
            "No se encuentra el PC. Comprueba que tiene FreedomPlayer abierto y que los dos estáis en la misma wifi."
        }

    /** Lo que llega del otro dispositivo, ya juntado con lo de aquí (ver LibrarySync). */
    fun applySync(merged: LibraryData) {
        if (isDemo) return
        var changed = false
        merged.songs.forEach { s ->
            val i = songs.indexOfFirst { it.id == s.id }
            if (i < 0) {
                songs.add(s)
                store.saveSong(s)
                if (s.score != null) store.setScore(s.id, s.score)
                changed = true
            } else if (songs[i] != s) {
                val before = songs[i]
                songs[i] = s
                store.saveSong(s)
                if (before.score != s.score) store.setScore(s.id, s.score)
                changed = true
            }
        }
        val keep = merged.songs.map { it.id }.toSet()
        songs.filter { it.id !in keep }.forEach { gone ->
            songs.remove(gone)
            store.deleteSong(gone.id)
            changed = true
        }
        if (merged.playlists != playlists.toList()) {
            playlists.filter { p -> merged.playlists.none { it.id == p.id } }.forEach { store.deletePlaylist(it.id) }
            playlists.clear()
            playlists.addAll(merged.playlists)
            playlists.forEachIndexed { i, p -> store.savePlaylist(p, i) }
        }
        if (merged.rankings != rankings.toList()) {
            rankings.filter { r -> merged.rankings.none { it.id == r.id } }.forEach { store.deleteRanking(it.id) }
            rankings.clear()
            rankings.addAll(merged.rankings)
            rankings.forEachIndexed { i, r -> store.saveRanking(r, i) }
        }
        val known = playLog.toHashSet()
        merged.plays.filter { it !in known }.forEach {
            playLog.add(it)
            store.addPlay(it)
            changed = true
        }
        if (changed) historyVersion++
    }

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

    init {
        applySound()
        restoreQueue()
    }

    /** Un paso del reloj: el audio simulado avanza y lo que pasa en el reproductor llega a la pantalla. */
    internal fun step(elapsedMs: Long) {
        applySound()
        engine.tick(elapsedMs)
        sync(elapsedMs)
        tickSleep(elapsedMs)
        checkGhosts()
    }

    /**
     * Hace correr el tiempo: reproducción, vista previa, avisos, búsquedas y descargas.
     * La app lo lanza al empezar y se para al cerrarla.
     */
    suspend fun run() {
        coroutineScope {
            scope = this
            launch {
                snapshotFlow { Triple(query.trim(), searchTab, online to searchRetry) }.collectLatest { (q, tab, net) ->
                    if (q.isEmpty() || !net.first || YouTubeLinks.playlistUrl(q) != null) return@collectLatest
                    delay(if (isDemo) 50 else 350)
                    search(q, tab)
                }
            }
            if (downloads.isNotEmpty() && worker?.isActive != true) worker = launch { work() }
            launch { measureLoudness() }
            launch { watchUpdates() }
            // Ya vinculado con otro dispositivo: se sincroniza solo al abrir la app.
            if (services.sync != null && store.setting(SYNC_PEER) != null) startSync()
            try {
                var mark = TimeSource.Monotonic.markNow()
                while (isActive) {
                    delay(100)
                    val elapsed = mark.elapsedNow().inWholeMilliseconds
                    mark = TimeSource.Monotonic.markNow()
                    step(elapsed.coerceIn(0L, 1_000L))
                }
            } finally {
                saveQueue()
                scope = null
                worker = null
            }
        }
    }

    /** Fuente de la cola para un resultado de búsqueda de vídeo (para el título de «SONANDO»). */
    fun isVideo(r: SearchResult): Boolean = r.kind == ResultKind.Video
}
