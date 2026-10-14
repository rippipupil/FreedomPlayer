package com.freedomplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.freedomplayer.core.MatchKind
import com.freedomplayer.core.PlaylistSort
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.YearMonth
import com.freedomplayer.ui.components.Mood
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueba de humo de la interfaz: pinta la app entera con el tamaño de un móvil y de un PC y pasa
 * por todas las pantallas, pestañas, paneles, hojas, avisos y estados. Falla si algo revienta al
 * componer, medir o dibujar. El reloj de la prueba avanza a mano para que las animaciones
 * infinitas (fantasmas, neón) no la dejen esperando para siempre.
 */
class AppSmokeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: AppState, wide: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.requiredSize(if (wide) 1280.dp else 390.dp, if (wide) 800.dp else 844.dp)) {
                App(state, wide = wide, version = "test")
            }
        }
        settle()
    }

    /** Deja correr las animaciones (y la reproducción simulada) y pinta un fotograma. */
    private fun settle(millis: Long = 700) {
        rule.mainClock.advanceTimeBy(millis)
        rule.onRoot().captureToImage()
    }

    @Test
    fun mobileWalkthrough() {
        val state = AppState()
        show(state, wide = false)
        walkThrough(state)
    }

    @Test
    fun desktopWalkthrough() {
        val state = AppState()
        show(state, wide = true)
        walkThrough(state)
    }

    @Test
    fun mobileStatesWithReducedMotion() {
        val state = AppState().apply { reduceMotion = true }
        show(state, wide = false)
        states(state)
    }

    @Test
    fun desktopStatesWithReducedMotion() {
        val state = AppState().apply { reduceMotion = true }
        show(state, wide = true)
        states(state)
    }

    private val menu = listOf(Screen.Search, Screen.Rank, Screen.Stats, Screen.Settings, Screen.Home)

    private fun walkThrough(state: AppState) {
        // El menú de los paneles
        for (screen in menu) {
            state.go(screen)
            settle()
            assertEquals(screen, state.screen)
        }
        state.openPanel(Side.Left)
        settle()
        state.panelFilter = "neon"
        settle()
        state.panelFilter = ""
        state.openPanel(Side.Right)
        settle()
        state.closePanel()
        settle()

        // Buscar: pestañas, búsquedas sin resultados, escuchar antes y añadir
        state.go(Screen.Search)
        for (tab in SearchTab.entries) {
            state.searchTab = tab
            settle()
        }
        state.searchTab = SearchTab.Songs
        state.query = ""
        settle()
        state.query = "zzzz"
        settle()
        state.query = "luz"
        settle()
        val song = SampleData.catalog.first { it.kind == ResultKind.Cancion && state.librarySongFor(it) == null }
        state.togglePreview(song)
        settle()
        assertNotNull(state.preview)
        // La vista previa también se abre en el reproductor, y se cierra con él.
        state.playerOpen = true
        settle()
        state.togglePlay()
        settle()
        state.seekTo(0.3f)
        settle()
        state.stopPreview()
        settle()
        assertFalse(state.playerOpen)
        state.requestAdd(AddTarget.Result(song))
        settle()
        assertNotNull(state.sheet)
        state.confirmAdd(AddTarget.Result(song), null)
        settle(3_000)

        val video = SampleData.catalog.first { it.kind == ResultKind.Video }
        state.searchTab = SearchTab.Videos
        state.query = ""
        settle()
        state.togglePreview(video)
        settle()
        state.stopPreview()
        state.requestAdd(AddTarget.Result(video))
        settle()
        state.sheet = null
        settle()

        val album = SampleData.albums.first()
        state.searchTab = SearchTab.Albums
        settle()
        // Entrar en el álbum y volver
        state.openAlbum(album)
        settle(1_000)
        state.back()
        settle()
        state.requestAdd(AddTarget.Album(album))
        settle()
        state.confirmAdd(AddTarget.Album(album), state.playlists.first().id)
        settle(2_000)

        // Listas de otros: buscarlas, abrir una, «+ AÑADIR TODAS» y un enlace pegado
        state.searchTab = SearchTab.Playlists
        state.query = "luz"
        settle(1_000)
        val other = SampleData.otherLists.first()
        state.openOtherList(other.url, other)
        settle(1_000)
        state.otherList?.list?.let { list ->
            state.requestAdd(AddTarget.WholeList(list))
            settle()
            state.sheet = null
            settle()
        }
        state.back()
        settle()
        state.query = other.url
        settle()
        assertNotNull(state.linkedList)
        state.query = ""
        settle()

        // Nueva playlist y nuevo ranking
        state.prompt = Prompt.NewPlaylist()
        settle()
        state.submitPrompt("Prueba")
        settle()
        assertNull(state.prompt)
        state.prompt = Prompt.NewRanking
        settle()
        state.submitPrompt("Ranking de prueba")
        settle()

        // Ranking: las tres pestañas, puntuar y deshacer
        state.go(Screen.Rank)
        for (tab in RankTab.entries) {
            state.rankTab = tab
            settle()
        }
        state.rankTab = RankTab.General
        val rated = state.library.first()
        val before = rated.score
        state.editingScoreId = rated.id
        settle()
        state.rate(rated.id, if (before == 7) 8 else 7)
        settle()
        state.runToastAction()
        settle()
        assertEquals(before, state.song(rated.id)?.score)
        state.rankTab = RankTab.Mine
        state.rankings.firstOrNull()?.let { r ->
            if (r.songIds.size > 1) state.moveInRanking(r.id, 0, 1)
        }
        settle()

        // Estadísticas
        state.go(Screen.Stats)
        state.statsTab = StatsTab.AllTime
        settle()
        state.statsTab = StatsTab.Monthly
        settle()
        state.statsMonth = YearMonth.of(nowMillis()).plus(-2)
        settle()
        state.statsTab = StatsTab.Recent
        settle()

        // Detalle de playlist
        val playlist = state.playlists.first()
        state.go(Screen.PlaylistDetail(playlist.id))
        settle()
        state.sortPlaylistsBy(PlaylistSort.Score)
        settle()
        state.flipPlaylistOrder()
        settle()
        state.sortPlaylistsBy(PlaylistSort.Added)
        settle()
        state.playPlaylist(playlist.id, shuffle = true)
        settle()

        // Reproductor (y puntuar la que suena)
        state.playerOpen = true
        settle()
        state.currentSong?.let { state.rate(it.id, 7) }
        settle()
        if (!state.isPlaying) state.togglePlay()
        settle(1_500)
        state.next()
        settle()
        state.cycleRepeat()
        settle()
        state.cycleRepeat()
        settle()
        state.toggleShuffle()
        settle()
        state.seekTo(0.5f)
        settle()
        state.sheet = Sheet.More
        settle()
        state.back()
        settle()
        state.playerOpen = false
        settle()

        // Importar de Spotify: enlace malo, enlace bueno y confirmar una dudosa
        state.go(Screen.SpotifyImport)
        settle()
        state.spotify.link = "esto no es un enlace"
        state.startSpotifyImport()
        settle()
        assertNotNull(state.spotify.error)
        state.spotify.link = "https://open.spotify.com/playlist/1a2B3c4D5e6F7g8H9i0J"
        state.startSpotifyImport()
        settle(15_000)
        assertEquals(ImportPhase.Done, state.spotify.phase)
        val doubtful = state.spotify.matches.indexOfFirst { it.kind == MatchKind.Dudosa }
        if (doubtful >= 0) {
            state.decideMatch(doubtful, true)
            settle()
        }

        // Avisos
        state.toast("Prueba", Mood.Feliz, Tone.Jade)
        settle()
        state.showError("No se pudo descargar", "Prueba", retry = {})
        settle()
        state.dismissError()
        settle(3_000)

        // Atrás hasta Inicio
        var steps = 0
        while (state.back() && steps++ < 50) settle()
        assertEquals(Screen.Home, state.screen)
    }

    /** Sin conexión, biblioteca vacía y reproductor, con el movimiento reducido. */
    private fun states(state: AppState) {
        state.settings.demoOffline = true
        settle()
        assertTrue(!state.online)
        for (screen in menu) {
            state.go(screen)
            settle()
        }
        state.setDemoEmpty(true)
        settle()
        for (screen in menu + Screen.SpotifyImport) {
            state.go(screen)
            settle()
        }
        state.openPanel(Side.Left)
        settle()
        state.closePanel()
        settle()
        state.setDemoEmpty(false)
        state.settings.demoOffline = false
        settle()
        state.playFrom(state.library.map { it.id }, state.library.first().id, "PRUEBA")
        state.playerOpen = true
        settle(1_500)
        state.playerOpen = false
        settle()
    }
}
