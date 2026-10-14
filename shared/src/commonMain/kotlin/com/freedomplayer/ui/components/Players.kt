package com.freedomplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.EqPreset
import com.freedomplayer.core.formatTime
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Sheet
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalFpFonts
import com.freedomplayer.core.RepeatMode as Repeat

/** Lo que muestran el mini-reproductor y la barra del PC: la canción o la vista previa. */
@Immutable
data class NowPlaying(
    val id: String,
    val title: String,
    val sub: String,
    val isPreview: Boolean,
    val coverColor: Long,
    val durationSec: Int,
    val cover: String? = null,
)

fun AppState.nowPlaying(): NowPlaying? {
    val p = preview
    if (p != null) {
        val sec = if (previewDurationMs > 0) ((previewDurationMs + 500) / 1000).toInt() else p.durationSec
        return NowPlaying(p.id, p.title, "Vista previa", true, p.coverColor, sec, p.cover)
    }
    val s = currentSong ?: return null
    return NowPlaying(s.id, s.title, s.artist, false, s.coverColor, currentDurationSec, s.cover)
}

fun AppState.nowPositionMs(): Long = if (preview != null) previewPositionMs else positionMs

fun AppState.nowIsPlaying(): Boolean = if (preview != null) previewPlaying else isPlaying

/** Botón grande rosa de reproducir/pausar (44 en el mini, 46 en el PC, 68 en el reproductor). */
@Composable
fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, size: Dp, cutSize: Dp, iconSize: Dp, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .size(size)
            .fpClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (playing) "Pausar" else "Reproducir" }
            .background(if (enabled) Fp.Neon else Fp.Neon.copy(alpha = 0.35f), cut(cutSize)),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = playing, animationSpec = tween(Motion.QUICK), label = "playIcon") { p ->
            FpIconView(if (p) FpIcons.Pause else FpIcons.Play, iconSize, Fp.Ink)
        }
    }
}

/** Aleatorio: rosa con un puntito debajo cuando está encendido. */
@Composable
fun ShuffleButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = 20.dp) {
    ModeButton(
        icon = FpIcons.Shuffle,
        active = on,
        label = "Aleatorio",
        state = if (on) "Activado" else "Desactivado",
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        badge = null,
    )
}

/** Repetir: no, todo, una. Un puntito marca que está activo y un «1» que repite una sola. */
@Composable
fun RepeatButton(mode: Repeat, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = 20.dp) {
    ModeButton(
        icon = FpIcons.Repeat,
        active = mode != Repeat.Off,
        label = "Repetir",
        state = when (mode) {
            Repeat.Off -> "No"
            Repeat.All -> "Toda la cola"
            Repeat.One -> "Solo esta canción"
        },
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        badge = if (mode == Repeat.One) "1" else null,
    )
}

@Composable
private fun ModeButton(
    icon: FpIcon,
    active: Boolean,
    label: String,
    state: String,
    onClick: () -> Unit,
    modifier: Modifier,
    size: Dp,
    iconSize: Dp,
    badge: String?,
) {
    val color by animateColorAsState(if (active) Fp.Neon else Fp.Idle, fpTween(), label = "modeColor")
    Box(
        modifier
            .size(size)
            .fpClickable(onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        FpIconView(icon, iconSize, color)
        AnimatedVisibility(
            visible = active,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            enter = fadeIn(tween(Motion.QUICK)) + scaleIn(tween(Motion.QUICK)),
            exit = fadeOut(tween(Motion.QUICK)) + scaleOut(tween(Motion.QUICK)),
        ) {
            Box(Modifier.size(4.dp).background(Fp.Neon))
        }
        AnimatedVisibility(
            visible = badge != null,
            modifier = Modifier.align(Alignment.Center).offset(x = iconSize / 2 + 2.dp, y = -(iconSize / 2)),
            enter = fadeIn(tween(Motion.QUICK)) + scaleIn(tween(Motion.QUICK), initialScale = 0.4f),
            exit = fadeOut(tween(Motion.QUICK)) + scaleOut(tween(Motion.QUICK), targetScale = 0.4f),
        ) {
            Chakra(badge ?: "1", 10.sp, color = Fp.Neon, weight = FontWeight.Bold)
        }
    }
}

/** «Artista · 1:24 / 3:21», con el tiempo en Chakra y el actual en rosa. */
@Composable
private fun subLine(sub: String, subColor: Color, posSec: Int, durationSec: Int): AnnotatedString {
    val chakra = LocalFpFonts.current.chakra
    return buildAnnotatedString {
        withStyle(SpanStyle(color = subColor)) { append("$sub · ") }
        withStyle(SpanStyle(fontFamily = chakra, color = Fp.Pink)) { append(formatTime(posSec)) }
        withStyle(SpanStyle(fontFamily = chakra, color = subColor)) { append(" / ${formatTime(durationSec)}") }
    }
}

/**
 * Mini-reproductor del móvil (componente 10): caja de 82 dp con carátula, título,
 * «Artista · 1:24 / 3:21», pausa, siguiente y la barra de 36 segmentos. El fantasma contento
 * asoma 18 dp por arriba. Mide 118 dp: 18 del fantasma, 82 de la caja y 18 de margen abajo
 * (donde también se puede tocar la barra).
 */
@Composable
fun MiniPlayer(state: AppState, modifier: Modifier = Modifier, onOpen: () -> Unit = { state.playerOpen = true }) {
    val now = state.nowPlaying() ?: return
    val posSec by remember(state) { derivedStateOf { (state.nowPositionMs() / 1000).toInt() } }
    val shape = cut(16.dp)
    val reduce = LocalReduceMotion.current
    Box(modifier.fillMaxWidth().height(118.dp)) {
        Box(
            Modifier
                .offset(y = 18.dp)
                .fillMaxWidth()
                .height(82.dp)
                .background(Fp.Panel, shape)
                .border(1.dp, Fp.Pink.copy(alpha = 0.35f), shape),
        )
        SeekBar(
            fraction = { state.nowPositionMs() / (now.durationSec * 1000f).coerceAtLeast(1f) },
            onSeek = { state.seekTo(it) },
            segments = 36,
            valueText = "${formatTime(posSec)} de ${formatTime(now.durationSec)}",
            modifier = Modifier.offset(y = 64.5.dp).padding(start = 16.dp, end = 22.dp),
            segmentHeight = 3.dp,
            knob = 9.dp,
        )
        Row(
            Modifier.offset(y = 18.dp).fillMaxWidth().height(64.dp).padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(
                now.coverColor,
                46.dp,
                Modifier
                    .fpClickable(onClick = onOpen)
                    .semantics { contentDescription = "Abrir reproductor" },
                image = now.cover,
            )
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
                    .fpClickable(pressedScale = 0.98f, onClickLabel = "Abrir reproductor", onClick = onOpen),
            ) {
                AnimatedContent(
                    targetState = now,
                    contentKey = { "${it.id}-${it.isPreview}" },
                    transitionSpec = {
                        val change = if (reduce) {
                            fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                        } else {
                            (fadeIn(tween(Motion.STANDARD)) + slideInVertically(tween(Motion.STANDARD, easing = Motion.Standard)) { it / 3 }) togetherWith
                                (fadeOut(tween(Motion.QUICK)) + slideOutVertically(tween(Motion.QUICK)) { -it / 3 })
                        }
                        change using SizeTransform(clip = false)
                    },
                    label = "miniTitle",
                ) { n ->
                    Column {
                        SongTitle(n.title)
                        Nunito(subLine(n.sub, if (n.isPreview) Fp.NeonSoft else Fp.Mist, posSec, n.durationSec), 12.sp)
                    }
                }
            }
            // Los mandos juntos, para dejarle sitio al título.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Un toque: al principio de la canción; dos seguidos: la anterior (ver AppState.rewind).
                IconButton(
                    FpIcons.PreviousMini,
                    "Atrás: un toque vuelve a empezar, dos van a la anterior",
                    onClick = { state.rewind() },
                    iconSize = 18.dp,
                    color = Fp.Bone,
                )
                PlayPauseButton(state.nowIsPlaying(), { state.togglePlay() }, size = 44.dp, cutSize = 10.dp, iconSize = 16.dp)
                IconButton(
                    FpIcons.NextMini,
                    if (now.isPreview) "Cerrar vista previa" else "Siguiente",
                    onClick = { state.next() },
                    iconSize = 18.dp,
                    color = Fp.Bone,
                )
            }
        }
        Ghost(Mood.Contento, 28.dp, Modifier.offset(x = 32.dp).fpFloat().fpHop(state.reaction))
    }
}

/**
 * Barra del reproductor del PC (92 dp): carátula con el fantasma contento, controles con la
 * barra de 60 segmentos, letra, ecualizador y volumen de 8 segmentos.
 */
@Composable
fun PlayerBar(state: AppState, modifier: Modifier = Modifier) {
    val now = state.nowPlaying()
    val posSec by remember(state) { derivedStateOf { (state.nowPositionMs() / 1000).toInt() } }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(Fp.Bar)
            .drawBehind { drawRect(Fp.Pink.copy(alpha = 0.25f), size = Size(this.size.width, 1.dp.toPx())) },
    ) {
        val side = (maxWidth * 0.265f).coerceIn(230.dp, 340.dp)
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            // Izquierda: lo que suena.
            Row(
                Modifier.width(side).fpClickable(enabled = now != null, pressedScale = 0.98f, onClickLabel = "Abrir reproductor") {
                    state.playerOpen = !state.playerOpen
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box {
                    Cover(now?.coverColor ?: 0xFF241A38, 56.dp, image = now?.cover)
                    Ghost(Mood.Contento, 30.dp, Modifier.offset(x = 40.dp, y = (-20).dp).fpFloat().fpHop(state.reaction))
                }
                AnimatedContent(
                    targetState = now,
                    contentKey = { "${it?.id}-${it?.isPreview}" },
                    transitionSpec = { fadeIn(tween(Motion.STANDARD)) togetherWith fadeOut(tween(Motion.QUICK)) },
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    label = "barTitle",
                ) { n ->
                    Column {
                        SongTitle(n?.title ?: "Nada sonando", size = 15.sp)
                        Nunito(
                            n?.sub ?: "Elige una canción en los paneles",
                            13.sp,
                            color = if (n?.isPreview == true) Fp.NeonSoft else Fp.Mist,
                            maxLines = 1,
                        )
                    }
                }
            }
            // Centro: controles y progreso.
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ShuffleButton(state.queue.shuffle, { state.toggleShuffle() }, size = 40.dp, iconSize = 18.dp)
                    IconButton(FpIcons.Previous, "Anterior", { state.previous() }, size = 40.dp, iconSize = 20.dp, color = Fp.Bone, enabled = now != null)
                    PlayPauseButton(state.nowIsPlaying(), { state.togglePlay() }, size = 46.dp, cutSize = 11.dp, iconSize = 18.dp, enabled = now != null)
                    IconButton(FpIcons.Next, if (now?.isPreview == true) "Cerrar vista previa" else "Siguiente", { state.next() }, size = 40.dp, iconSize = 20.dp, color = Fp.Bone, enabled = now != null)
                    RepeatButton(state.queue.repeat, { state.cycleRepeat() }, size = 40.dp, iconSize = 18.dp)
                }
                Row(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Chakra(formatTime(posSec), 12.sp, Modifier.width(34.dp), color = Fp.Pink, align = TextAlign.End)
                    Box(Modifier.weight(1f)) {
                        if (now != null) {
                            SeekBar(
                                fraction = { state.nowPositionMs() / (now.durationSec * 1000f).coerceAtLeast(1f) },
                                onSeek = { state.seekTo(it) },
                                segments = 60,
                                valueText = "${formatTime(posSec)} de ${formatTime(now.durationSec)}",
                                segmentHeight = 4.dp,
                                knob = 10.dp,
                                touchHeight = 28.dp,
                            )
                        } else {
                            SegmentBar(60, 0f, Modifier.padding(vertical = 12.dp))
                        }
                    }
                    Chakra(formatTime(now?.durationSec ?: 0), 12.sp, Modifier.width(34.dp), color = Fp.Mist)
                }
            }
            // Derecha: letra, ecualizador y volumen.
            Row(Modifier.width(side), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    FpIcons.Lyrics,
                    "Letra",
                    { state.playerOpen = !state.playerOpen },
                    size = 40.dp,
                    iconSize = 18.dp,
                    color = if (state.playerOpen) Fp.Neon else Fp.Idle,
                    enabled = state.currentSong != null,
                )
                Box(Modifier.width(6.dp))
                IconButton(
                    FpIcons.Equalizer,
                    "Ecualizador",
                    { state.sheet = Sheet.Equalizer },
                    size = 40.dp,
                    iconSize = 18.dp,
                    color = if (EqPreset.matching(state.settings.eqGains) != EqPreset.Flat) Fp.Neon else Fp.Idle,
                )
                Box(Modifier.width(8.dp))
                IconButton(
                    if (state.muted || state.volume == 0f) FpIcons.Mute else FpIcons.Volume,
                    if (state.muted) "Activar sonido" else "Silenciar",
                    { state.muted = !state.muted },
                    size = 32.dp,
                    iconSize = 18.dp,
                )
                VolumeBar(
                    value = if (state.muted) 0f else state.volume,
                    onChange = {
                        state.volume = it
                        state.muted = false
                    },
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}
