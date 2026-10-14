package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.LyricLine
import com.freedomplayer.core.Lyrics
import com.freedomplayer.core.Song
import com.freedomplayer.core.formatTime
import com.freedomplayer.ui.AddTarget
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Prompt
import com.freedomplayer.ui.Sheet
import com.freedomplayer.ui.blockTouchesBelow
import com.freedomplayer.ui.components.AddButton
import com.freedomplayer.ui.components.AlleyBackground
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.CoverImage
import com.freedomplayer.ui.components.FpIcon
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.IconButton
import com.freedomplayer.ui.components.InLibraryBadge
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.LocalScreenInsets
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.PlayPauseButton
import com.freedomplayer.ui.components.RepeatButton
import com.freedomplayer.ui.components.SeekBar
import com.freedomplayer.ui.components.ShuffleButton
import com.freedomplayer.ui.components.StarsInput
import com.freedomplayer.ui.components.coverModel
import com.freedomplayer.ui.components.cut
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.fpHop
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.nowIsPlaying
import com.freedomplayer.ui.components.nowPlaying
import com.freedomplayer.ui.components.nowPositionMs
import com.freedomplayer.ui.components.statusBarHeight
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide
import kotlinx.coroutines.delay

/**
 * El reproductor: carátula grande, datos y nota (se puntúa aquí mismo), barra de 48 segmentos,
 * controles, la letra sincronizada y las herramientas. En el móvil ocupa toda la pantalla; en el
 * PC es una página entre la barra de título y la barra del reproductor, con la letra al lado.
 *
 * Con una vista previa sonando enseña esa canción: sin nota, cola ni herramientas, y con «+» para
 * añadirla.
 */
@Composable
fun PlayerScreen(state: AppState, modifier: Modifier = Modifier) {
    val p = state.preview
    val song = if (p != null) Song(p.id, p.title, p.artist, p.durationSec, p.coverColor, cover = p.cover) else state.currentSong ?: return
    val preview = p != null
    // Al cerrarse el reproductor, la letra a pantalla completa se cierra con él.
    DisposableEffect(Unit) { onDispose { state.lyricsOpen = false } }
    val reduce = LocalReduceMotion.current
    Box(modifier) {
        if (LocalWide.current) PlayerWide(state, song, preview, Modifier) else PlayerMobile(state, song, preview, Modifier)
        AnimatedVisibility(
            visible = state.lyricsOpen && !preview,
            enter = if (reduce) fadeIn(tween(Motion.QUICK)) else fadeIn(tween(Motion.STANDARD)) + scaleIn(tween(Motion.STANDARD, easing = Motion.Standard), initialScale = 0.96f),
            exit = if (reduce) fadeOut(tween(Motion.QUICK)) else fadeOut(tween(Motion.QUICK)) + scaleOut(tween(Motion.QUICK), targetScale = 0.98f),
        ) {
            LyricsFull(state, song)
        }
    }
}

/**
 * La letra a pantalla completa: el callejón con el color de la canción, título y artista, la
 * letra grande (se mueve a mano y vuelve a seguir sola), la barra y reproducir/pausar.
 */
@Composable
private fun LyricsFull(state: AppState, song: Song) {
    val wide = LocalWide.current
    val top = if (wide) 18.dp else statusBarHeight() + 12.dp
    val bottom = if (wide) 20.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp
    val side = if (wide) LocalScreenInsets.current.wideSide else 28.dp
    Box(Modifier.fillMaxSize().background(Fp.Night).blockTouchesBelow()) {
        AlleyBackground(Modifier.fillMaxSize(), wide = wide, flicker = false, tint = Color(song.coverColor))
        Column(
            Modifier.fillMaxSize().padding(start = side, end = side, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    FpIcons.ChevronDown,
                    "Cerrar la letra",
                    { state.lyricsOpen = false },
                    Modifier.offset(x = (-10).dp),
                    iconSize = 22.dp,
                    color = Fp.Bone,
                )
                Column(Modifier.weight(1f)) {
                    Nunito(song.title, if (wide) 22.sp else 18.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                    Nunito(song.artist, 13.sp, color = Fp.Mist, maxLines = 1)
                }
                PlayPauseButton(state.isPlaying, { state.togglePlay() }, size = 48.dp, cutSize = 11.dp, iconSize = 18.dp)
            }
            LyricsBox(
                state,
                song,
                visibleLines = if (wide) 10 else 8,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                big = true,
            )
            Progress(state, segments = if (wide) 60 else 40)
        }
    }
}

/** «SONANDO · NOCHES DE NEÓN» (o «VISTA PREVIA») con ⌄ para cerrar y ⋯ para más opciones. */
@Composable
private fun PlayerTopBar(state: AppState, preview: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            FpIcons.ChevronDown,
            "Cerrar reproductor",
            { state.playerOpen = false },
            Modifier.offset(x = (-10).dp),
            iconSize = 22.dp,
            color = Fp.Bone,
        )
        Chakra(
            if (preview) "VISTA PREVIA" else "SONANDO · ${state.queueSource}",
            if (LocalWide.current) 12.sp else 11.sp,
            Modifier.weight(1f).padding(horizontal = 6.dp),
            color = Fp.Pink,
            spacing = 3.sp,
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (preview) {
            // Las opciones son de las canciones de la biblioteca; se deja el hueco para que el título siga centrado.
            Spacer(Modifier.size(44.dp))
        } else {
            IconButton(
                FpIcons.More,
                "Más opciones",
                { state.sheet = Sheet.More },
                Modifier.offset(x = 10.dp),
                iconSize = 20.dp,
                color = Fp.Bone,
            )
        }
    }
}

// ---------- Móvil ----------

@Composable
private fun PlayerMobile(state: AppState, song: Song, preview: Boolean, modifier: Modifier) {
    val top = statusBarHeight()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(Fp.Night)) {
        AlleyBackground(Modifier.fillMaxSize(), wide = false, flicker = false, tint = Color(song.coverColor))
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 34.dp, end = 34.dp, top = top + 12.dp, bottom = bottom + 20.dp)) {
            // La carátula mide 268 dp como en el diseño, o menos si la pantalla es baja o estrecha
            // (lo demás ocupa unos 520 dp contando las ✦ para puntuar y un mínimo para la letra).
            val coverSize = minOf(268.dp, maxWidth - 20.dp, maxHeight - 520.dp).coerceAtLeast(120.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                PlayerTopBar(state, preview)
                // El título va 18 px por debajo de la carátula, como en el diseño: el marco rosa
                // desplazado se sale por debajo sin ocupar sitio.
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    BigCover(state, song, coverSize, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                    SongInfo(state, song, preview)
                }
                Progress(state, segments = 48)
                Controls(state, preview, big = true)
                LyricsBox(
                    state,
                    song,
                    visibleLines = 4,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    preview = preview,
                    onExpand = { state.lyricsOpen = true },
                )
                if (!preview) Tools(state)
            }
        }
    }
}

// ---------- PC ----------

@Composable
private fun PlayerWide(state: AppState, song: Song, preview: Boolean, modifier: Modifier) {
    val insets = LocalScreenInsets.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = insets.wideSide
        val coverSize = minOf(320.dp, maxHeight * 0.5f, (maxWidth - side * 2) * 0.38f).coerceAtLeast(160.dp)
        Column(
            Modifier.fillMaxSize().padding(start = side, end = side, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PlayerTopBar(state, preview)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(
                    Modifier.width(coverSize + 12.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    BigCover(state, song, coverSize, Modifier.padding(top = 22.dp, bottom = 10.dp))
                    SongInfo(state, song, preview, Modifier.padding(top = 8.dp))
                    if (!preview) Tools(state)
                }
                LyricsBox(
                    state,
                    song,
                    visibleLines = 9,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    big = true,
                    preview = preview,
                    onExpand = { state.lyricsOpen = true },
                )
            }
        }
    }
}

// ---------- Piezas ----------

/**
 * Carátula grande con esquinas cortadas (22 dp), marco rosa desplazado detrás, sombra y el
 * fantasma feliz flotando en la esquina. Al cambiar de canción, la nueva entra con un fundido.
 */
@Composable
private fun BigCover(state: AppState, song: Song, size: Dp, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val k = size / 268.dp
    // Mide lo que la carátula: el marco desplazado (10 dp) se dibuja fuera, sin ocupar sitio.
    Box(modifier.size(size)) {
        Box(
            Modifier
                .offset(x = 10.dp * k, y = 10.dp * k)
                .size(size)
                .border(1.dp, Fp.Neon.copy(alpha = 0.6f), cut(22.dp * k)),
        )
        AnimatedContent(
            targetState = song,
            contentKey = { it.id },
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                } else {
                    (fadeIn(tween(Motion.SLOW)) + scaleIn(tween(Motion.SLOW, easing = Motion.Standard), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(Motion.STANDARD)) + scaleOut(tween(Motion.STANDARD), targetScale = 1.03f)) using SizeTransform(clip = false)
                }
            },
            label = "bigCover",
        ) { s ->
            CoverArt(s.coverColor, size, s.cover)
        }
        Ghost(
            Mood.Feliz,
            44.dp,
            Modifier
                .offset(x = size - 36.dp, y = (-26).dp)
                .fpFloat()
                .fpHop(state.reaction),
        )
    }
}

/**
 * La carátula de la canción con las esquinas cortadas. Sin imagen, el dibujo del diseño (luna,
 * edificios y destello) sobre el color de la canción.
 */
@Composable
private fun CoverArt(color: Long, size: Dp, image: String?) {
    val shape = cut(22.dp * (size / 268.dp))
    if (coverModel(image) != null) {
        Box(
            Modifier
                .size(size)
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Color(color)),
        ) {
            CoverImage(image, Modifier.fillMaxSize())
        }
        return
    }
    val base = Color(color)
    val dark1 = lerp(base, Color.Black, 0.37f)
    val dark2 = lerp(base, Color.Black, 0.5f)
    Canvas(
        Modifier
            .size(size)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(base),
    ) {
        val s = this.size.width / 268f
        drawCircle(Fp.Neon.copy(alpha = 0.35f), radius = 46f * s, center = Offset(190f * s, 80f * s))
        drawRect(dark1, topLeft = Offset(30f * s, 150f * s), size = Size(70f * s, 118f * s))
        drawRect(dark2, topLeft = Offset(104f * s, 120f * s), size = Size(54f * s, 148f * s))
        drawRect(dark1, topLeft = Offset(164f * s, 170f * s), size = Size(80f * s, 98f * s))
        val star = Path().apply {
            moveTo(134f * s, 20f * s)
            lineTo(138f * s, 36f * s)
            lineTo(154f * s, 40f * s)
            lineTo(138f * s, 44f * s)
            lineTo(134f * s, 60f * s)
            lineTo(130f * s, 44f * s)
            lineTo(114f * s, 40f * s)
            lineTo(130f * s, 36f * s)
            close()
        }
        drawPath(star, Fp.Lamp.copy(alpha = 0.8f))
    }
}

/**
 * Título (Nunito 24), «Artista · Álbum» y debajo la nota en ✦, que se toca para puntuar mientras
 * suena (mitad izquierda de una ✦: media). En la vista previa, «+» para añadirla.
 */
@Composable
private fun SongInfo(state: AppState, song: Song, preview: Boolean, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AnimatedContent(
            targetState = song,
            contentKey = { it.id },
            transitionSpec = { fadeIn(tween(Motion.STANDARD)) togetherWith fadeOut(tween(Motion.QUICK)) using SizeTransform(clip = false) },
            modifier = Modifier.fillMaxWidth(),
            label = "songInfo",
        ) { s ->
            Column {
                Nunito(
                    s.title,
                    if (wide) 28.sp else 24.sp,
                    Modifier.semantics { heading() },
                    weight = FontWeight.ExtraBold,
                    maxLines = 2,
                    lineHeight = if (wide) 32.sp else 28.sp,
                )
                Nunito(
                    if (s.album != null) "${s.artist} · ${s.album}" else s.artist,
                    14.sp,
                    Modifier.padding(top = 2.dp),
                    color = if (preview) Fp.NeonSoft else Fp.Mist,
                    maxLines = 1,
                )
            }
        }
        if (preview) PreviewAdd(state) else Rating(state, song)
    }
}

/** Las ✦ para puntuar la que suena (se guarda al soltar, con «DESHACER» en el aviso). */
@Composable
private fun Rating(state: AppState, song: Song) {
    val score = state.song(song.id)?.score
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Las casillas son más anchas que las ✦: se corre a la izquierda para que la primera quede alineada con el título.
        StarsInput(
            score = score,
            onRate = { state.rate(song.id, it) },
            label = "Nota para ${song.title}",
            modifier = Modifier.offset(x = (-9).dp),
            cell = 38.dp,
            starSize = 20.dp,
        )
        Chakra(
            if (score == null) "SIN NOTA" else "$score/10",
            11.sp,
            Modifier.offset(x = (-9).dp),
            color = if (score == null) Fp.MistDim else Fp.Pink,
            spacing = 1.5.sp,
        )
    }
}

/** Vista previa: «+» para añadirla (con el mismo selector de playlist que en Buscar) o EN BIBLIO si ya está. */
@Composable
private fun PreviewAdd(state: AppState) {
    val r = state.previewTarget ?: return
    val inLibrary = state.librarySongFor(r) != null && r.id !in state.addedIds
    Row(
        Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (inLibrary) {
            InLibraryBadge()
        } else {
            AddButton(state.addStateFor(r), "Añadir a la biblioteca", onClick = { state.requestAdd(AddTarget.Result(r)) })
            Nunito("Añádela para guardarla con su carátula y su letra.", 13.sp, color = Fp.Mist, lineHeight = 17.sp)
        }
    }
}

/** La barra mide 44 dp para el dedo, pero en la columna ocupa [visible] (como en el diseño). */
private fun Modifier.visualHeight(visible: Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val h = visible.roundToPx()
    layout(placeable.width, h) { placeable.place(0, (h - placeable.height) / 2) }
}

/** Barra de segmentos con el rombo y los tiempos debajo (el actual en rosa). Sirve también para la vista previa. */
@Composable
private fun Progress(state: AppState, segments: Int) {
    val posSec by remember(state) { derivedStateOf { (state.nowPositionMs() / 1000).toInt() } }
    val total = state.nowPlaying()?.durationSec ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SeekBar(
            fraction = { state.nowPositionMs() / ((state.nowPlaying()?.durationSec ?: 0) * 1000f).coerceAtLeast(1f) },
            onSeek = { state.seekTo(it) },
            segments = segments,
            valueText = "${formatTime(posSec)} de ${formatTime(total)}",
            modifier = Modifier.visualHeight(24.dp),
            segmentHeight = 4.dp,
            knob = 12.dp,
            touchHeight = 44.dp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Chakra(formatTime(posSec), 12.sp, color = Fp.Pink, weight = FontWeight.Medium)
            Chakra(formatTime(total), 12.sp, color = Fp.Mist, weight = FontWeight.Medium)
        }
    }
}

/**
 * Aleatorio, anterior, reproducir/pausar (68 dp), siguiente y repetir. En la vista previa no hay
 * cola: «anterior» vuelve al principio y «siguiente» la cierra.
 */
@Composable
private fun Controls(state: AppState, preview: Boolean, big: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (preview) Spacer(Modifier.size(44.dp)) else ShuffleButton(state.queue.shuffle, { state.toggleShuffle() })
        IconButton(FpIcons.Previous, if (preview) "Volver a empezar" else "Anterior", { state.previous() }, size = 52.dp, iconSize = 24.dp, color = Fp.Bone)
        PlayPauseButton(
            state.nowIsPlaying(),
            { state.togglePlay() },
            size = if (big) 68.dp else 56.dp,
            cutSize = if (big) 16.dp else 13.dp,
            iconSize = 24.dp,
        )
        IconButton(FpIcons.Next, if (preview) "Cerrar vista previa" else "Siguiente", { state.next() }, size = 52.dp, iconSize = 24.dp, color = Fp.Bone)
        if (preview) Spacer(Modifier.size(44.dp)) else RepeatButton(state.queue.repeat, { state.cycleRepeat() })
    }
}

/**
 * La letra (LRCLIB, guardada para escucharla sin conexión): la línea que suena, grande y blanca;
 * las anteriores apagadas y las siguientes en gris violeta. Al avanzar, las líneas suben deslizando.
 * Se puede mover a mano (con el dedo o la rueda): mientras tanto no sigue a la canción, y vuelve
 * a seguirla [FOLLOW_AGAIN_MS] después de soltar. Con [onExpand], «⤢» la abre a pantalla completa.
 */
@Composable
private fun LyricsBox(
    state: AppState,
    song: Song,
    visibleLines: Int,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    preview: Boolean = false,
    onExpand: (() -> Unit)? = null,
) {
    // La vista previa no tiene letra guardada: se busca al añadirla.
    val data = if (preview) null else state.lyricsFor(song.id)
    val lines = remember(data) { data?.lines().orEmpty() }
    // Sin tiempos (o con la letra sincronizada apagada en Ajustes) se ve entera, sin resaltar.
    val synced = state.settings.syncedLyrics && lines.firstOrNull()?.atMs != LyricLine.UNSYNCED
    val current by remember(state, lines, synced) {
        derivedStateOf { if (synced) Lyrics.currentIndex(lines, state.positionMs + 150) else -1 }
    }
    val start = if (synced) Lyrics.windowStart(lines, current, visibleLines) else 0
    val reduce = LocalReduceMotion.current
    Column(
        modifier
            .heightIn(min = 96.dp)
            .background(Color(0xC7161024))
            .drawBehind { drawRect(Fp.Neon, size = Size(2.dp.toPx(), this.size.height)) }
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Chakra("LETRA", 11.sp, color = Fp.Pink, spacing = 3.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chakra(if (lines.isEmpty()) "LRCLIB" else "LRCLIB · OFFLINE", 11.sp, color = Fp.MistDim, spacing = 3.sp)
                if (onExpand != null && lines.isNotEmpty()) {
                    IconButton(
                        FpIcons.Expand,
                        "Ver la letra a pantalla completa",
                        onExpand,
                        Modifier.offset(x = 8.dp),
                        size = 36.dp,
                        iconSize = 16.dp,
                        color = Fp.Pink,
                    )
                }
            }
        }
        if (lines.isEmpty()) {
            NoLyrics(instrumental = data?.instrumental == true, online = state.online, preview = preview)
        } else {
            val scroll = rememberScrollState()
            val tops = remember(song.id) { mutableStateMapOf<Int, Int>() }
            val target = tops[start]
            // Mientras la persona la mueve (dedo o rueda), deja de seguir a la canción.
            var follow by remember { mutableStateOf(true) }
            var touched by remember { mutableIntStateOf(0) }
            LaunchedEffect(song.id) { follow = true }
            LaunchedEffect(touched) {
                if (touched == 0) return@LaunchedEffect
                delay(FOLLOW_AGAIN_MS)
                follow = true
            }
            LaunchedEffect(start, target, follow) {
                val y = target ?: return@LaunchedEffect
                if (!follow) return@LaunchedEffect
                if (reduce) scroll.scrollTo(y) else scroll.animateScrollTo(y, tween(Motion.SLOW, easing = Motion.Standard))
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                val byHand = e.type == PointerEventType.Scroll || e.type == PointerEventType.Press ||
                                    (e.type == PointerEventType.Move && e.changes.any { it.pressed })
                                if (byHand) {
                                    follow = false
                                    touched++
                                }
                            }
                        }
                    }
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(if (big) 12.dp else 8.dp),
            ) {
                lines.forEachIndexed { i, line ->
                    LyricText(
                        text = line.text,
                        state = when {
                            !synced -> LineState.Plain
                            i == current -> LineState.Now
                            i < current -> LineState.Past
                            else -> LineState.Next
                        },
                        big = big,
                        modifier = Modifier.onPlaced { tops[i] = it.positionInParent().y.toInt() },
                    )
                }
                // Hueco al final para que las últimas líneas puedan subir hasta arriba.
                Spacer(Modifier.height(if (big) 240.dp else 120.dp))
            }
        }
    }
}

/** Después de mover la letra a mano, vuelve a seguir a la canción pasado este tiempo. */
private const val FOLLOW_AGAIN_MS = 4_000L

private enum class LineState { Past, Now, Next, Plain }

@Composable
private fun LyricText(text: String, state: LineState, big: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        when (state) {
            LineState.Now -> Color.White
            LineState.Past -> Fp.LyricPast
            LineState.Next -> Fp.MistDim
            LineState.Plain -> Fp.Mist
        },
        fpTween(Motion.SLOW),
        label = "lyricColor",
    )
    val base = if (big) 19f else 15f
    val size by animateFloatAsState(if (state == LineState.Now) base + 4f else base, fpTween(Motion.SLOW), label = "lyricSize")
    Nunito(text, size.sp, modifier, color = color, weight = FontWeight.ExtraBold, lineHeight = (size * 1.3f).sp)
}

/** Sin letra todavía (la busca LRCLIB al descargar o al escucharla con conexión). */
@Composable
private fun ColumnScope.NoLyrics(instrumental: Boolean, online: Boolean, preview: Boolean = false) {
    Row(
        Modifier.weight(1f).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ghost(Mood.Dormido, 36.dp, Modifier.fpFloat())
        Nunito(
            when {
                preview -> "La letra llega al añadirla: se busca en LRCLIB y se guarda con la canción."
                instrumental -> "Es instrumental: no tiene letra."
                online -> "LRCLIB no tiene la letra de esta canción. Se vuelve a mirar dentro de unos días."
                else -> "Esta canción no tiene letra todavía. Se buscará en LRCLIB cuando haya conexión."
            },
            13.sp,
            Modifier.widthIn(max = 320.dp),
            color = Fp.Mist,
            lineHeight = 18.sp,
        )
    }
}

/** Las herramientas del reproductor. */
private enum class Tool(val icon: FpIcon, val label: String) {
    Eq(FpIcons.Equalizer, "Ecualizador"),
    Sleep(FpIcons.Moon, "Temporizador de apagado"),
    Edit(FpIcons.Pencil, "Editar datos de la canción"),
    Queue(FpIcons.Queue, "Cola"),
}

/** Ecualizador, temporizador (en Neón mientras está puesto), editar datos y cola. */
@Composable
private fun Tools(state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(Fp.Pink.copy(alpha = 0.18f), size = Size(this.size.width, 1.dp.toPx())) }
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (tool in Tool.entries) {
            val on = tool == Tool.Sleep && state.sleepTimer != null
            IconButton(
                tool.icon,
                if (on) tool.label + " (puesto)" else tool.label,
                { useTool(state, tool) },
                color = if (on) Fp.Neon else Fp.Idle,
            )
        }
    }
}

private fun useTool(state: AppState, tool: Tool) {
    when (tool) {
        Tool.Eq -> state.sheet = Sheet.Equalizer
        Tool.Sleep -> state.sheet = Sheet.SleepTimer
        Tool.Edit -> state.currentSong?.let { state.prompt = Prompt.EditSong(it.id) }
        Tool.Queue -> state.sheet = Sheet.Queue
    }
}
