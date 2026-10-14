package com.freedomplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.freedomplayer.ui.components.AlleyBackground
import com.freedomplayer.ui.components.Centered
import com.freedomplayer.ui.components.DesktopPanels
import com.freedomplayer.ui.components.EdgeZone
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.LocalScreenInsets
import com.freedomplayer.ui.components.MiniPlayer
import com.freedomplayer.ui.components.MobilePanels
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.NoticeStack
import com.freedomplayer.ui.components.OfflineSlot
import com.freedomplayer.ui.components.PlayerBar
import com.freedomplayer.ui.components.ScreenInsets
import com.freedomplayer.ui.components.SheetHost
import com.freedomplayer.ui.components.SideTabs
import com.freedomplayer.ui.components.nowPlaying
import com.freedomplayer.ui.screens.HomeScreen
import com.freedomplayer.ui.screens.PlayerScreen
import com.freedomplayer.ui.screens.PlaylistScreen
import com.freedomplayer.ui.screens.RankingScreen
import com.freedomplayer.ui.screens.SearchScreen
import com.freedomplayer.ui.screens.SettingsScreen
import com.freedomplayer.ui.screens.SpotifyScreen
import com.freedomplayer.ui.screens.StatsScreen
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.FreedomTheme

/** Versión de la app («0.1.12»), para Ajustes › Acerca de. */
val LocalAppVersion = staticCompositionLocalOf { "dev" }

/**
 * La app entera, igual en el móvil y en el PC. [wide] elige la disposición del PC (barra de
 * título propia, bordes que abren los paneles y barra del reproductor abajo). [titleBar] es esa
 * barra de título, que pone la ventana del PC.
 */
@Composable
fun App(
    state: AppState,
    wide: Boolean,
    modifier: Modifier = Modifier,
    version: String = "dev",
    titleBar: (@Composable () -> Unit)? = null,
    /** En Android el bucle vive en el proceso (sigue con la pantalla apagada); aquí, con la ventana. */
    runLoop: Boolean = true,
) {
    FreedomTheme(wide) {
        CompositionLocalProvider(
            LocalReduceMotion provides state.reduceMotion,
            LocalAppVersion provides version,
        ) {
            if (runLoop) LaunchedEffect(state) { state.run() }
            Box(modifier.fillMaxSize().background(Fp.Night)) {
                if (wide) DesktopShell(state, titleBar) else MobileShell(state)
            }
        }
    }
}

/** Para que una capa de encima (el reproductor) no deje pasar los toques a lo de debajo. */
private fun Modifier.blockTouchesBelow(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}

/** La pantalla actual, con una transición corta al cambiar (hacia delante o hacia atrás). */
@Composable
private fun ScreenHost(state: AppState, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = state.screen,
        modifier = modifier,
        transitionSpec = {
            if (reduce) {
                fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
            } else {
                val d = if (state.navForward) 1 else -1
                (fadeIn(tween(Motion.SLOW, easing = Motion.Standard)) + slideInHorizontally(tween(Motion.SLOW, easing = Motion.Standard)) { d * it / 12 }) togetherWith
                    (fadeOut(tween(Motion.QUICK)) + slideOutHorizontally(tween(Motion.STANDARD, easing = Motion.Standard)) { -d * it / 12 })
            }
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(state)
            Screen.Search -> SearchScreen(state)
            Screen.Rank -> RankingScreen(state)
            Screen.Stats -> StatsScreen(state)
            Screen.Settings -> SettingsScreen(state)
            is Screen.PlaylistDetail -> PlaylistScreen(state, screen.id)
            Screen.SpotifyImport -> SpotifyScreen(state)
        }
    }
}

// ---------- Móvil ----------

/**
 * Móvil: el callejón de fondo, la pantalla, las pestañas laterales, los avisos, el
 * mini-reproductor y, por encima, el reproductor, los paneles y las hojas.
 */
@Composable
private fun MobileShell(state: AppState) {
    val reduce = LocalReduceMotion.current
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val showMini = state.nowPlaying() != null
    val playerVisible = state.playerOpen && state.currentSong != null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AlleyBackground(Modifier.fillMaxSize(), wide = false, flicker = state.screen == Screen.Home && !playerVisible)
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(status))
            OfflineSlot(state, Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp))
            CompositionLocalProvider(
                LocalScreenInsets provides ScreenInsets(
                    top = 20.dp,
                    bottom = nav + if (showMini) 126.dp else 28.dp,
                ),
            ) {
                ScreenHost(state, Modifier.weight(1f).fillMaxWidth())
            }
        }
        // Las pestañas, a media altura (286 de 844 en el diseño), sin bajar hasta el mini-reproductor.
        val tabsTop = minOf(maxHeight * (286f / 844f), maxHeight - nav - 118.dp - 214.dp).coerceAtLeast(status + 120.dp)
        SideTabs(state, top = tabsTop)
        NoticeStack(
            state,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = nav + if (showMini) 116.dp else 18.dp),
        )
        AnimatedVisibility(
            visible = showMini,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (reduce) fadeIn(tween(Motion.QUICK)) else slideInVertically(tween(Motion.SLOW, easing = Motion.Standard)) { it } + fadeIn(tween(Motion.STANDARD)),
            exit = if (reduce) fadeOut(tween(Motion.QUICK)) else slideOutVertically(tween(Motion.STANDARD, easing = Motion.Standard)) { it } + fadeOut(tween(Motion.QUICK)),
        ) {
            MiniPlayer(state, Modifier.padding(start = 14.dp, end = 14.dp, bottom = nav))
        }
        AnimatedVisibility(
            visible = playerVisible,
            enter = if (reduce) fadeIn(tween(Motion.QUICK)) else slideInVertically(tween(Motion.SLOW, easing = Motion.Standard)) { it } + fadeIn(tween(Motion.STANDARD)),
            exit = if (reduce) fadeOut(tween(Motion.QUICK)) else slideOutVertically(tween(Motion.STANDARD, easing = Motion.Standard)) { it } + fadeOut(tween(Motion.STANDARD)),
        ) {
            PlayerScreen(state, Modifier.blockTouchesBelow())
        }
        MobilePanels(state)
        SheetHost(state)
    }
}

// ---------- PC ----------

/**
 * PC: barra de título, el centro (pantalla o reproductor con letra), los bordes que abren los
 * paneles al dejar el ratón 1 s (o con un clic en su pestañita) y la barra del reproductor.
 */
@Composable
private fun DesktopShell(state: AppState, titleBar: (@Composable () -> Unit)?) {
    val reduce = LocalReduceMotion.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            titleBar?.invoke()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val side = when {
                    maxWidth >= 1100.dp -> 96.dp
                    maxWidth >= 860.dp -> 64.dp
                    else -> 40.dp
                }
                val playerVisible = state.playerOpen && state.currentSong != null
                AlleyBackground(Modifier.fillMaxSize(), wide = true, flicker = state.screen == Screen.Home && !playerVisible)
                Column(Modifier.fillMaxSize()) {
                    OfflineSlot(
                        state,
                        Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).padding(start = 40.dp, end = 40.dp, top = 10.dp),
                    )
                    CompositionLocalProvider(LocalScreenInsets provides ScreenInsets(top = 36.dp, bottom = 28.dp, wideSide = side)) {
                        Centered(maxWidth = 1440.dp, modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = playerVisible,
                                transitionSpec = {
                                    if (reduce) {
                                        fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                                    } else if (targetState) {
                                        (fadeIn(tween(Motion.SLOW)) + slideInVertically(tween(Motion.SLOW, easing = Motion.Standard)) { it / 10 }) togetherWith
                                            fadeOut(tween(Motion.QUICK))
                                    } else {
                                        fadeIn(tween(Motion.SLOW)) togetherWith
                                            (fadeOut(tween(Motion.QUICK)) + slideOutVertically(tween(Motion.STANDARD, easing = Motion.Standard)) { it / 10 })
                                    }
                                },
                                label = "pcPlayer",
                            ) { open ->
                                if (open) PlayerScreen(state, Modifier.fillMaxSize()) else ScreenHost(state, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                NoticeStack(
                    state,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 480.dp)
                        .padding(start = 40.dp, end = 40.dp, bottom = 16.dp),
                )
                EdgeZone(state, Side.Left, Modifier.align(Alignment.CenterStart))
                EdgeZone(state, Side.Right, Modifier.align(Alignment.CenterEnd))
                DesktopPanels(state)
            }
            PlayerBar(state)
        }
        SheetHost(state)
    }
}
