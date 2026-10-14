package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import com.freedomplayer.core.Lyrics
import com.freedomplayer.core.LyricLine
import com.freedomplayer.core.Song
import com.freedomplayer.core.formatTime
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Prompt
import com.freedomplayer.ui.Sheet
import com.freedomplayer.ui.components.AlleyBackground
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.FpIcon
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.IconButton
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.LocalScreenInsets
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.PlayPauseButton
import com.freedomplayer.ui.components.RepeatButton
import com.freedomplayer.ui.components.SeekBar
import com.freedomplayer.ui.components.ShuffleButton
import com.freedomplayer.ui.components.CoverImage
import com.freedomplayer.ui.components.coverModel
import com.freedomplayer.ui.components.StarsView
import com.freedomplayer.ui.components.cut
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.fpHop
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/**
 * El reproductor: carátula grande, datos y nota, barra de 48 segmentos, controles, la letra
 * sincronizada y las herramientas. En el móvil ocupa toda la pantalla; en el PC es una página
 * entre la barra de título y la barra del reproductor, con la letra al lado.
 */
@Composable
fun PlayerScreen(state: AppState, modifier: Modifier = Modifier) {
    val song = state.currentSong ?: return
    if (LocalWide.current) PlayerWide(state, song, modifier) else PlayerMobile(state, song, modifier)
}

/** «SONANDO · NOCHES DE NEÓN» con ⌄ para cerrar y ⋯ para más opciones. */
@Composable
private fun PlayerTopBar(state: AppState, modifier: Modifier = Modifier) {
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
            "SONANDO · ${state.queueSource}",
            if (LocalWide.current) 12.sp else 11.sp,
            Modifier.weight(1f).padding(horizontal = 6.dp),
            color = Fp.Pink,
            spacing = 3.sp,
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
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

// ---------- Móvil ----------

@Composable
private fun PlayerMobile(state: AppState, song: Song, modifier: Modifier) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(Fp.Night)) {
        AlleyBackground(Modifier.fillMaxSize(), wide = false, flicker = false, tint = Color(song.coverColor))
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 34.dp, end = 34.dp, top = top + 12.dp, bottom = bottom + 20.dp)) {
            // La carátula mide 268 dp como en el diseño, o menos si la pantalla es baja o estrecha
            // (lo demás ocupa unos 480 dp contando un mínimo para la letra).
            val coverSize = minOf(268.dp, maxWidth - 20.dp, maxHeight - 480.dp).coerceAtLeast(120.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                PlayerTopBar(state)
                BigCover(state, song, coverSize, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                SongInfo(song, Modifier.padding(top = 6.dp))
                Progress(state, song, segments = 48)
                Controls(state, big = true)
                LyricsBox(state, song, visibleLines = 4, modifier = Modifier.weight(1f).fillMaxWidth())
                Tools(state)
            }
        }
    }
}

// ---------- PC ----------

@Composable
private fun PlayerWide(state: AppState, song: Song, modifier: Modifier) {
    val insets = LocalScreenInsets.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = insets.wideSide
        val coverSize = minOf(320.dp, maxHeight * 0.5f, (maxWidth - side * 2) * 0.38f).coerceAtLeast(160.dp)
        Column(
            Modifier.fillMaxSize().padding(start = side, end = side, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PlayerTopBar(state)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(
                    Modifier.width(coverSize + 12.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    BigCover(state, song, coverSize, Modifier.padding(top = 22.dp))
                    SongInfo(song, Modifier.padding(top = 8.dp))
                    Tools(state)
                }
                LyricsBox(state, song, visibleLines = 9, modifier = Modifier.weight(1f).fillMaxHeight(), big = true)
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
    Box(modifier.size(size + 10.dp * k, size + 10.dp * k)) {
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

/** Título (Nunito 24), «Artista · Álbum» y la nota en ✦ (solo se ve; se cambia en Ranking). */
@Composable
private fun SongInfo(song: Song, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        AnimatedContent(
            targetState = song,
            contentKey = { it.id },
            transitionSpec = { fadeIn(tween(Motion.STANDARD)) togetherWith fadeOut(tween(Motion.QUICK)) using SizeTransform(clip = false) },
            modifier = Modifier.weight(1f),
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
                    color = Fp.Mist,
                    maxLines = 1,
                )
            }
        }
        StarsView(song.score, 14.dp, Modifier.padding(bottom = 4.dp), gap = 1.dp)
    }
}

/** La barra mide 44 dp para el dedo, pero en la columna ocupa [visible] (como en el diseño). */
private fun Modifier.visualHeight(visible: Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val h = visible.roundToPx()
    layout(placeable.width, h) { placeable.place(0, (h - placeable.height) / 2) }
}

/** Barra de segmentos con el rombo y los tiempos debajo (el actual en rosa). */
@Composable
private fun Progress(state: AppState, song: Song, segments: Int) {
    val posSec by remember(state) { derivedStateOf { (state.positionMs / 1000).toInt() } }
    val total = state.currentDurationSec
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SeekBar(
            fraction = { state.positionMs / (state.currentDurationSec * 1000f).coerceAtLeast(1f) },
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

/** Aleatorio, anterior, reproducir/pausar (68 dp), siguiente y repetir. */
@Composable
private fun Controls(state: AppState, big: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ShuffleButton(state.queue.shuffle, { state.toggleShuffle() })
        IconButton(FpIcons.Previous, "Anterior", { state.previous() }, size = 52.dp, iconSize = 24.dp, color = Fp.Bone)
        PlayPauseButton(
            state.isPlaying,
            { state.togglePlay() },
            size = if (big) 68.dp else 56.dp,
            cutSize = if (big) 16.dp else 13.dp,
            iconSize = 24.dp,
        )
        IconButton(FpIcons.Next, "Siguiente", { state.next() }, size = 52.dp, iconSize = 24.dp, color = Fp.Bone)
        RepeatButton(state.queue.repeat, { state.cycleRepeat() })
    }
}

/**
 * La letra (LRCLIB, guardada para escucharla sin conexión): la línea que suena, grande y blanca;
 * las anteriores apagadas y las siguientes en gris violeta. Al avanzar, las líneas suben deslizando.
 */
@Composable
private fun LyricsBox(state: AppState, song: Song, visibleLines: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    val data = state.lyricsFor(song.id)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Chakra("LETRA", 11.sp, color = Fp.Pink, spacing = 3.sp)
            Chakra(if (lines.isEmpty()) "LRCLIB" else "LRCLIB · OFFLINE", 11.sp, color = Fp.MistDim, spacing = 3.sp)
        }
        if (lines.isEmpty()) {
            NoLyrics(instrumental = data?.instrumental == true, online = state.online)
        } else {
            val scroll = rememberScrollState()
            val tops = remember(song.id) { mutableStateMapOf<Int, Int>() }
            val target = tops[start]
            LaunchedEffect(start, target) {
                val y = target ?: return@LaunchedEffect
                if (reduce) scroll.scrollTo(y) else scroll.animateScrollTo(y, tween(Motion.SLOW, easing = Motion.Standard))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().verticalScroll(scroll, enabled = false),
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
private fun ColumnScope.NoLyrics(instrumental: Boolean, online: Boolean) {
    Row(
        Modifier.weight(1f).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ghost(Mood.Dormido, 36.dp, Modifier.fpFloat())
        Nunito(
            when {
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
        Tool.Eq -> state.soon("El ecualizador")
        Tool.Sleep -> state.sheet = Sheet.SleepTimer
        Tool.Edit -> state.currentSong?.let { state.prompt = Prompt.EditSong(it.id) }
        Tool.Queue -> state.sheet = Sheet.Queue
    }
}
