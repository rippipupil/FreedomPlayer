package com.freedomplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.EqPreset
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.Spotify
import com.freedomplayer.core.Stats
import com.freedomplayer.data.SampleSource
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.theme.Fp
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pinta las pantallas principales con los datos de ejemplo, al tamaño de las capturas del diseño
 * (móvil 390×844 y PC 1280×800, a 1×), para compararlas con `diseno/capturas`. Se ejecuta con
 * las demás pruebas (así se sabe que todo se puede pintar); con la variable FP_CAPTURAS guarda
 * los PNG en esa carpeta (lo hace el job «capturas» de CI, que los sube a la rama capturas-ci).
 */
@OptIn(ExperimentalComposeUiApi::class)
class CapturasTest {
    private val out: File? = System.getenv("FP_CAPTURAS")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

    /** Avanza el reloj de la escena: deja correr las animaciones y pinta. */
    private class Clock(val scene: ImageComposeScene) {
        var nanos = 0L

        fun frames(millis: Long) {
            val end = nanos + millis * 1_000_000
            while (nanos < end) {
                Snapshot.sendApplyNotifications()
                scene.render(nanos)
                nanos += 16_000_000
            }
        }
    }

    private val titleBar: @Composable () -> Unit = { StaticTitleBar() }

    /** Pinta una captura: [setup] deja la app como en el diseño y luego pasan [settleMs] de animaciones. */
    private fun shoot(name: String, wide: Boolean, settleMs: Long = 1_200, setup: AppState.(Clock) -> Unit) {
        val state = AppState()
        val width = if (wide) 1280 else 390
        val height = if (wide) 800 else 844
        val bar = if (wide) titleBar else null
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            // En el móvil, con la barra de estado de 24 que deja el diseño arriba.
            App(state, wide = wide, version = "0.3", titleBar = bar, runLoop = false, statusBar = if (wide) null else 24.dp)
        }
        try {
            val clock = Clock(scene)
            clock.frames(300)
            state.setup(clock)
            clock.frames(settleMs)
            Snapshot.sendApplyNotifications()
            val png = scene.render(clock.nanos).encodeToData(EncodedImageFormat.PNG)?.bytes
            assertTrue(png != null && png.size > 2_000, "$name: no se ha pintado nada")
            out?.let { File(it, "$name.png").writeBytes(png) }
        } finally {
            scene.close()
        }
    }

    private fun AppState.searchFor(query: String, tab: SearchTab) {
        go(Screen.Search)
        this.query = query
        searchTab = tab
        runBlocking {
            search(query, tab)
            // Sin el bucle de la app, las canciones de cada álbum se piden aquí («N CANCIONES»).
            if (tab == SearchTab.Albums) {
                val source = SampleSource(latencyMs = 0)
                albumResults.items.forEach { albumTracks[it.key] = source.albumTracks(it) }
            }
        }
    }

    private fun result(id: String) = SampleData.catalog.first { it.id == id }

    /** «Luz de farola (versión acústica)» sonando en vista previa por el 1:18, como en el diseño. */
    private fun AppState.previewAcoustic() {
        togglePreview(result("c1"))
        step(78_000)
    }

    @Test
    fun phone() {
        shoot("01-movil-inicio", wide = false) { togglePlay() }
        shoot("01b-movil-inicio-panel-izquierdo-canciones", wide = false) {
            togglePlay()
            openPanel(Side.Left)
        }
        shoot("01c-movil-inicio-panel-derecho-playlists", wide = false) {
            togglePlay()
            openPanel(Side.Right)
        }
        shoot("02-movil-buscar-canciones", wide = false) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Songs)
            previewAcoustic()
        }
        shoot("02b-movil-buscar-anadida-aviso", wide = false) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Songs)
            previewAcoustic()
            val r = AddTarget.Result(result("c1"))
            requestAdd(r)
            confirmAdd(r, null)
        }
        shoot("02c-movil-buscar-sin-vista-previa", wide = false) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Songs)
        }
        shoot("02d-movil-buscar-albumes", wide = false) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Albums)
        }
        shoot("02e-movil-buscar-videos", wide = false) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Videos)
        }
        shoot("03-movil-ranking-general", wide = false) {
            togglePlay()
            go(Screen.Rank)
        }
        shoot("03b-movil-ranking-mios", wide = false) {
            togglePlay()
            go(Screen.Rank)
            rankTab = RankTab.Mine
        }
        shoot("03c-movil-ranking-sin-nota", wide = false) {
            togglePlay()
            go(Screen.Rank)
            rankTab = RankTab.Unrated
        }
        shoot("03d-movil-ranking-general-recien-puntuada", wide = false) {
            togglePlay()
            go(Screen.Rank)
            val song = library.first { it.score == null && it.plays > 0 }
            rate(song.id, 9)
        }
        shoot("03e-movil-ranking-sin-nota-todo-puntuado", wide = false) {
            togglePlay()
            go(Screen.Rank)
            rankTab = RankTab.Unrated
            library.filter { it.score == null }.forEach { rate(it.id, 7) }
            dismissToast()
        }
        shoot("04-movil-reproductor", wide = false) {
            togglePlay()
            playerOpen = true
        }
        shoot("04b-movil-reproductor-en-pausa", wide = false) { playerOpen = true }
        shoot("04c-movil-cola", wide = false) {
            togglePlay()
            playerOpen = true
            sheet = Sheet.Queue
        }
        shoot("04d-movil-temporizador", wide = false) {
            togglePlay()
            playerOpen = true
            startSleepTimer(30)
            dismissToast()
            sheet = Sheet.SleepTimer
        }
        shoot("04e-movil-ecualizador", wide = false) {
            togglePlay()
            playerOpen = true
            settings.eqGains = EqPreset.Rock.gains
            sheet = Sheet.Equalizer
        }
        shoot("05-movil-estados-sin-conexion", wide = false) {
            togglePlay()
            settings.demoOffline = true
        }
        shoot("05b-movil-estados-nada-por-aqui", wide = false) {
            togglePlay()
            searchFor("farolillo", SearchTab.Songs)
        }
        shoot("05c-movil-estados-error", wide = false) {
            togglePlay()
            go(Screen.Search)
            showError("No se pudo descargar «Farolas»", "YouTube ha cambiado algo. La app se actualizará sola; puedes reintentar.", retry = {})
        }
        shoot("05d-movil-estados-anadida", wide = false) {
            togglePlay()
            toast("«Luz de farola» ya está en tu biblioteca, con carátula y letra.", Mood.Owo)
        }
        shoot("05e-movil-estados-biblioteca-vacia", wide = false) { setDemoEmpty(true) }
        shoot("05f-movil-estadisticas", wide = false) {
            togglePlay()
            go(Screen.Stats)
        }
        shoot("05f2-movil-estadisticas-por-mes", wide = false) {
            togglePlay()
            go(Screen.Stats)
            statsTab = StatsTab.Monthly
        }
        shoot("05g-movil-ajustes", wide = false) {
            togglePlay()
            go(Screen.Settings)
        }
        // Importar de Spotify: el aviso si no se puede leer, buscando y las dudosas para confirmar.
        shoot("05h-movil-spotify-privada", wide = false) {
            go(Screen.SpotifyImport)
            spotify.link = "https://open.spotify.com/playlist/1a2B3c4D5e6F7g8H9i0J"
            spotify.error = "Spotify no enseña esa playlist: puede que sea privada o que ya no exista. " +
                "Hazla pública en Spotify y vuelve a probar."
        }
        shoot("05h2-movil-spotify-buscando", wide = false) {
            go(Screen.SpotifyImport)
            spotify.total = SampleData.spotifyTracks.size
            spotify.checked = 4
            spotify.phase = ImportPhase.Matching
        }
        shoot("05h3-movil-spotify-dudosas", wide = false) {
            go(Screen.SpotifyImport)
            spotify.matches.addAll(Spotify.matchAll(SampleData.spotifyTracks, SampleData.catalog))
            spotify.playlistId = createPlaylist(SampleData.SPOTIFY_PLAYLIST_NAME)
            spotify.note = "Spotify solo enseña 9 de las 12 canciones sin iniciar sesión: se han buscado esas."
            spotify.phase = ImportPhase.Done
        }
    }

    @Test
    fun desktop() {
        shoot("06-pc-inicio", wide = true) { togglePlay() }
        // El borde a medio cargar (la raya crece durante 1 s con el ratón encima).
        shoot("06b-pc-borde-izquierdo-cargando", wide = true, settleMs = 0) { clock ->
            togglePlay()
            clock.frames(100)
            clock.scene.sendPointerEvent(PointerEventType.Enter, Offset(8f, 400f))
            clock.scene.sendPointerEvent(PointerEventType.Move, Offset(9f, 400f))
            clock.frames(560)
        }
        shoot("06c-pc-panel-izquierdo-canciones", wide = true) {
            togglePlay()
            openPanel(Side.Left)
        }
        shoot("06d-pc-panel-derecho-playlists", wide = true) {
            togglePlay()
            openPanel(Side.Right)
        }
        shoot("06e-pc-buscar-canciones", wide = true) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Songs)
            previewAcoustic()
        }
        shoot("06f-pc-buscar-albumes", wide = true) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Albums)
        }
        shoot("06g-pc-buscar-videos", wide = true) {
            togglePlay()
            searchFor("luz de farola", SearchTab.Videos)
        }
        shoot("06h-pc-ranking-general", wide = true) {
            togglePlay()
            go(Screen.Rank)
        }
        shoot("06i-pc-ranking-mios", wide = true) {
            togglePlay()
            go(Screen.Rank)
            rankTab = RankTab.Mine
        }
        shoot("06j-pc-ranking-sin-nota", wide = true) {
            togglePlay()
            go(Screen.Rank)
            rankTab = RankTab.Unrated
        }
        shoot("06k-pc-reproductor", wide = true) {
            togglePlay()
            playerOpen = true
        }
        shoot("06k2-pc-cola", wide = true) {
            togglePlay()
            playerOpen = true
            sheet = Sheet.Queue
        }
        shoot("06k3-pc-ecualizador", wide = true) {
            togglePlay()
            settings.eqGains = EqPreset.Vocal.gains
            sheet = Sheet.Equalizer
        }
        shoot("06l-pc-estados-sin-conexion", wide = true) {
            togglePlay()
            settings.demoOffline = true
            toast("«Luz de farola» ya está en tu biblioteca, con carátula y letra.", Mood.Owo)
        }
        shoot("06m-pc-biblioteca-vacia", wide = true) { setDemoEmpty(true) }
        shoot("06n-pc-estadisticas", wide = true) {
            togglePlay()
            go(Screen.Stats)
        }
        shoot("06o-pc-estadisticas-por-mes", wide = true) {
            togglePlay()
            go(Screen.Stats)
            statsTab = StatsTab.Monthly
            statsMonth = Stats.months(playLog).getOrNull(1)
        }
    }

    /** La barra de título de la ventana del PC (la de verdad está en desktopApp), sin botones vivos. */
    @Composable
    private fun StaticTitleBar() {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Fp.Night.copy(alpha = 0.6f))
                .drawBehind {
                    val px = 1.dp.toPx()
                    drawRect(Fp.Pink.copy(alpha = 0.12f), topLeft = Offset(0f, size.height - px), size = Size(size.width, px))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FpIconView(FpIcons.Sparkle, 12.dp, Fp.Neon)
                Chakra("FREEDOMPLAYER", 12.sp, color = Fp.Pink, spacing = 3.sp)
            }
            for (icon in listOf(FpIcons.MiniWindow, FpIcons.Minimize, FpIcons.Maximize, FpIcons.CloseWindow)) {
                Box(Modifier.width(46.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    FpIconView(icon, 12.dp, Fp.Mist)
                }
            }
        }
    }
}
