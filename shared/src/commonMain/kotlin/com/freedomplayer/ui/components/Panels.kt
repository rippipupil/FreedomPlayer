package com.freedomplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.Search
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Prompt
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.Side
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide
import kotlinx.coroutines.delay

private class NavEntry(val screen: Screen, val icon: FpIcon, val label: String)

private val NavEntries = listOf(
    NavEntry(Screen.Home, FpIcons.Home, "Inicio"),
    NavEntry(Screen.Search, FpIcons.Search, "Buscar"),
    NavEntry(Screen.Rank, FpIcons.Ranking, "Ranking"),
    NavEntry(Screen.Stats, FpIcons.Stats, "Estadísticas"),
    NavEntry(Screen.Settings, FpIcons.Settings, "Ajustes"),
)

/** El menú de la app: 5 iconos arriba de los paneles. El activo va en rosa y subrayado. */
@Composable
fun NavRow(state: AppState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val px = 1.dp.toPx()
                val c = Fp.Pink.copy(alpha = 0.2f)
                drawRect(c, size = Size(this.size.width, px))
                drawRect(c, topLeft = Offset(0f, this.size.height - px), size = Size(this.size.width, px))
            }
            .semantics { contentDescription = "Menú" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (entry in NavEntries) {
            val active = state.screen == entry.screen
            val color by animateColorAsState(if (active) Fp.Neon else Fp.Idle, fpTween(), label = "navColor")
            val bar by animateFloatAsState(if (active) 1f else 0f, fpTween(), label = "navBar")
            val inset = if (wide) 14.dp else 12.dp
            Box(
                Modifier
                    .size(if (wide) 52.dp else 50.dp, if (wide) 48.dp else 50.dp)
                    .fpClickable(role = Role.Tab) { state.go(entry.screen) }
                    .semantics {
                        contentDescription = entry.label
                        selected = active
                    }
                    .drawBehind {
                        if (bar > 0f) {
                            val i = inset.toPx()
                            val full = this.size.width - i * 2
                            val w = full * bar
                            drawRect(
                                Fp.Neon,
                                topLeft = Offset(i + (full - w) / 2f, this.size.height - 1.dp.toPx()),
                                size = Size(w, 2.dp.toPx()),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                FpIconView(entry.icon, if (wide) 20.dp else 22.dp, color)
            }
        }
    }
}

/** «Canciones 248» / «Playlists 6». */
@Composable
private fun PanelTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Chakra(title, 22.sp, Modifier.alignByBaseline(), weight = FontWeight.Bold)
        Chakra(count.toString(), 12.sp, Modifier.alignByBaseline(), color = Fp.Mist, spacing = 1.5.sp, weight = FontWeight.Medium)
    }
}

/** Panel izquierdo: el menú, «Canciones», el filtro y la biblioteca de la A a la Z. */
@Composable
fun ColumnScope.SongsPanelContent(state: AppState) {
    val wide = LocalWide.current
    NavRow(state)
    PanelTitle("Canciones", state.library.size)
    UnderlineField(
        value = state.panelFilter,
        onValueChange = { state.panelFilter = it },
        placeholder = "Filtrar canciones",
        label = "Filtrar canciones",
        height = if (wide) 38.dp else 40.dp,
    )
    val filter = Search.normalize(state.panelFilter.trim())
    val list = state.library
        .filter { filter.isEmpty() || Search.normalize("${it.title} ${it.artist}").contains(filter) }
        .sortedBy { Search.normalize(it.title) }
    val ids = list.map { it.id }
    val current = state.currentSong?.id
    if (state.library.isEmpty()) {
        EmptyState(
            title = "Biblioteca vacía",
            body = "Busca tu primera canción o vincula el PC con un QR.",
            modifier = Modifier.weight(1f),
            ghosts = {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ghost(Mood.Feliz, 34.dp, Modifier.fpFloat())
                    Ghost(Mood.Dormido, 28.dp, Modifier.fpBob())
                }
            },
        ) {
            PrimaryButton("BUSCAR", { state.go(Screen.Search) }, height = 40.dp, cutSize = 10.dp, fontSize = 12.sp)
        }
    } else if (list.isEmpty()) {
        Nunito("Ninguna canción coincide con «${state.panelFilter.trim()}».", 13.sp, color = Fp.Mist)
        Box(Modifier.weight(1f))
    } else {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(list, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    playing = song.id == current,
                    onClick = { state.playFrom(ids, song.id, "TU BIBLIOTECA") },
                    modifier = itemMotion(),
                    coverSize = if (wide) 34.dp else 36.dp,
                    verticalPadding = if (wide) 6.dp else 7.dp,
                )
            }
        }
    }
    if (state.canImportFiles) {
        SecondaryButton("+ TUS ARCHIVOS DE AUDIO", { state.importFiles() }, Modifier.fillMaxWidth(), height = if (wide) 40.dp else 44.dp, fontSize = 12.sp)
    }
}

/** Panel derecho: el menú, «Playlists», la lista y los botones de crear e importar. */
@Composable
fun ColumnScope.PlaylistsPanelContent(state: AppState) {
    val wide = LocalWide.current
    NavRow(state)
    PanelTitle("Playlists", state.playlists.size)
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 10.dp)) {
        items(state.playlists, key = { it.id }) { p ->
            PlaylistRow(p, onClick = { state.go(Screen.PlaylistDetail(p.id)) }, modifier = itemMotion(), covers = state.coversOf(p))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
            "+ NUEVA PLAYLIST",
            { state.prompt = Prompt.NewPlaylist() },
            Modifier.fillMaxWidth(),
            fill = true,
            height = if (wide) 44.dp else 46.dp,
        )
        SecondaryButton("IMPORTAR DE SPOTIFY", { state.go(Screen.SpotifyImport) }, Modifier.fillMaxWidth())
    }
}

/**
 * Marco de un panel: fondo #161024, borde rosa en el lado interior con una marca más gruesa
 * y el fantasma que se asoma (guiño a la izquierda, OwO a la derecha).
 */
@Composable
private fun PanelFrame(
    left: Boolean,
    width: Dp,
    wide: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = if (wide) 22.dp else statusBarHeight() + 16.dp
    val bottom = if (wide) 22.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    Box(modifier.fillMaxHeight().width(width + 48.dp)) {
        val panelAlign = if (left) Alignment.TopStart else Alignment.TopEnd
        // Sombra del PC (20px 0 50px rgba(0,0,0,.5)) hacia fuera del panel.
        if (wide) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = if (left) width else 0.dp)
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            if (left) listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                            else listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        ),
                    ),
            )
        }
        Column(
            Modifier
                .align(panelAlign)
                .width(width)
                .fillMaxHeight()
                .background(Fp.Drawer)
                .drawBehind {
                    val px = 1.dp.toPx()
                    val x = if (left) this.size.width - px else 0f
                    drawRect(Fp.Neon.copy(alpha = 0.45f), topLeft = Offset(x, 0f), size = Size(px, this.size.height))
                    val markX = if (left) this.size.width - 3.dp.toPx() else -1.dp.toPx()
                    drawRect(Fp.Neon, topLeft = Offset(markX, 180.dp.toPx()), size = Size(4.dp.toPx(), 60.dp.toPx()))
                }
                .padding(
                    start = if (wide) 20.dp else if (left) 18.dp else 16.dp,
                    end = if (wide) 20.dp else if (left) 16.dp else 18.dp,
                    top = top,
                    bottom = bottom,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        // El fantasma que se asoma por el borde.
        val ghostX = if (left) width - (if (wide) 12.dp else 10.dp) else 48.dp - (if (wide) 22.dp else 24.dp)
        Ghost(
            if (left) Mood.Guino else Mood.Owo,
            34.dp,
            Modifier
                .offset(x = ghostX, y = if (wide) 70.dp else 92.dp)
                .fpPeek(fromX = if (left) (-26).dp else 26.dp, rotation = if (left) 14f else -14f),
        )
    }
}

/**
 * Paneles del móvil: se abren tocando su pestaña, oscurecen la pantalla (capa al 62 %) y se
 * cierran tocando fuera o con «atrás». Entran deslizando desde su borde.
 */
@Composable
fun MobilePanels(state: AppState, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = minOf(304.dp, maxWidth - 56.dp)
        AnimatedVisibility(
            visible = state.panel != null,
            enter = fadeIn(tween(Motion.SLOW, easing = Motion.Standard)),
            exit = fadeOut(tween(Motion.STANDARD, easing = Motion.Standard)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Fp.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Cerrar panel",
                    ) { state.closePanel() }
                    .semantics { contentDescription = "Cerrar panel" },
            )
        }
        AnimatedVisibility(
            visible = state.panel == Side.Left,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = if (reduce) fadeIn(tween(Motion.QUICK)) else slideInHorizontally(tween(Motion.SLOW, easing = Motion.Standard)) { -it },
            exit = if (reduce) fadeOut(tween(Motion.QUICK)) else slideOutHorizontally(tween(Motion.STANDARD, easing = Motion.Standard)) { -it },
        ) {
            PanelFrame(left = true, width = width, wide = false) { SongsPanelContent(state) }
        }
        AnimatedVisibility(
            visible = state.panel == Side.Right,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = if (reduce) fadeIn(tween(Motion.QUICK)) else slideInHorizontally(tween(Motion.SLOW, easing = Motion.Standard)) { it },
            exit = if (reduce) fadeOut(tween(Motion.QUICK)) else slideOutHorizontally(tween(Motion.STANDARD, easing = Motion.Standard)) { it },
        ) {
            PanelFrame(left = false, width = width, wide = false) { PlaylistsPanelContent(state) }
        }
    }
}

/**
 * Paneles del PC: 340 dp, entre la barra de título y la del reproductor, con sombra y sin
 * oscurecer el resto. Entran deslizando (200 ms) y se cierran al sacar el ratón.
 */
@Composable
fun DesktopPanels(state: AppState, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val shift = with(LocalDensity.current) { 24.dp.roundToPx() }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.panel == Side.Left,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(200, easing = Motion.EaseOut)) + (if (reduce) fadeIn(tween(1)) else slideInHorizontally(tween(200, easing = Motion.EaseOut)) { -shift }),
            exit = fadeOut(tween(Motion.QUICK)) + (if (reduce) fadeOut(tween(1)) else slideOutHorizontally(tween(Motion.QUICK)) { -shift }),
        ) {
            HoverPanel(state) { PanelFrame(left = true, width = 340.dp, wide = true) { SongsPanelContent(state) } }
        }
        AnimatedVisibility(
            visible = state.panel == Side.Right,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(200, easing = Motion.EaseOut)) + (if (reduce) fadeIn(tween(1)) else slideInHorizontally(tween(200, easing = Motion.EaseOut)) { shift }),
            exit = fadeOut(tween(Motion.QUICK)) + (if (reduce) fadeOut(tween(1)) else slideOutHorizontally(tween(Motion.QUICK)) { shift }),
        ) {
            HoverPanel(state) { PanelFrame(left = false, width = 340.dp, wide = true) { PlaylistsPanelContent(state) } }
        }
    }
}

/** Cierra el panel cuando el ratón sale de él (con un margen corto para no parpadear). */
@Composable
private fun HoverPanel(state: AppState, content: @Composable () -> Unit) {
    var inside by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(inside, entered) {
        if (entered && !inside) {
            delay(180)
            state.closePanel()
        }
    }
    Box(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move -> {
                            inside = true
                            entered = true
                        }
                        PointerEventType.Exit -> inside = false
                        else -> Unit
                    }
                }
            }
        },
    ) {
        content()
    }
}
