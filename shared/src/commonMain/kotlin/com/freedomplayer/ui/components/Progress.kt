package com.freedomplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freedomplayer.ui.theme.Fp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Barra de progreso de segmentos con el rombo claro como tirador (36 segmentos en el
 * mini-reproductor, 48 en el reproductor y 60 en el PC). Se toca o se arrastra para saltar.
 *
 * La posición se lee con [fraction] al dibujar, así que avanzar no recompone nada.
 * [lineY] es dónde va la línea dentro de la zona táctil (por defecto, en medio).
 */
@Composable
fun SeekBar(
    fraction: () -> Float,
    onSeek: (Float) -> Unit,
    segments: Int,
    valueText: String,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = 3.dp,
    knob: Dp = 9.dp,
    touchHeight: Dp = 44.dp,
    lineY: Dp? = null,
    label: String = "Posición de la canción",
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val read by rememberUpdatedState(fraction)
    val reduce = LocalReduceMotion.current
    // Los saltos grandes (otra canción, la vista previa, «anterior») se deslizan en 220 ms
    // en vez de saltar; el avance normal y el arrastre van tal cual.
    val blend = remember { Animatable(1f) }
    var from by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduce) {
        var last = read().coerceIn(0f, 1f)
        snapshotFlow { read().coerceIn(0f, 1f) }.collect { target ->
            if (!reduce && dragging == null && abs(target - last) > 0.02f) {
                from += (last - from) * blend.value
                launch {
                    blend.snapTo(0f)
                    blend.animateTo(1f, tween(Motion.STANDARD, easing = Motion.Standard))
                }
            }
            last = target
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(touchHeight)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var f = (down.position.x / width).coerceIn(0f, 1f)
                    dragging = f
                    seek(f)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val next = (change.position.x / width).coerceIn(0f, 1f)
                        if (next != f) {
                            f = next
                            dragging = f
                            seek(f)
                        }
                        change.consume()
                    }
                    dragging = null
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(read().coerceIn(0f, 1f), 0f..1f)
                setProgress { v ->
                    seek(v.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        val held = dragging
        val actual = (held ?: read()).coerceIn(0f, 1f)
        val b = blend.value
        val f = if (held != null || b >= 1f) actual else from + (actual - from) * b
        val cy = lineY?.toPx() ?: (this.size.height / 2f)
        drawSegments(f, segments, segmentHeight.toPx(), cy)
        drawKnob(f * this.size.width, cy, knob.toPx())
    }
}

private fun DrawScope.drawSegments(f: Float, count: Int, height: Float, cy: Float) {
    val gap = 2.dp.toPx()
    val w = (this.size.width - gap * (count - 1)) / count
    for (i in 0 until count) {
        val on = (i + 0.5f) / count <= f
        drawRect(
            if (on) Fp.Neon else Fp.Track,
            topLeft = Offset(i * (w + gap), cy - height / 2f),
            size = Size(w, height),
        )
    }
}

/** Rombo claro con brillo rosa. */
private fun DrawScope.drawKnob(x: Float, cy: Float, side: Float) {
    val c = Offset(x, cy)
    drawCircle(
        brush = Brush.radialGradient(listOf(Fp.Neon.copy(alpha = 0.55f), Color.Transparent), center = c, radius = side * 1.15f),
        radius = side * 1.15f,
        center = c,
    )
    rotate(45f, pivot = c) {
        drawRect(Fp.Lamp, topLeft = Offset(x - side / 2f, cy - side / 2f), size = Size(side, side))
    }
}

/**
 * Volumen del PC: 8 segmentos. Se toca, se arrastra o se usa la rueda del ratón.
 */
@Composable
fun VolumeBar(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, segments: Int = 8) {
    val change by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    Canvas(
        modifier
            .height(32.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    change((down.position.x / width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        change((c.position.x / width).coerceIn(0f, 1f))
                        c.consume()
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy != 0f) {
                                change((current - dy * 0.0625f).coerceIn(0f, 1f))
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .semantics {
                contentDescription = "Volumen"
                stateDescription = "${(value * 100).toInt()} %"
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v ->
                    change(v.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        val gap = 2.dp.toPx()
        val h = 4.dp.toPx()
        val w = (this.size.width - gap * (segments - 1)) / segments
        val cy = this.size.height / 2f
        for (i in 0 until segments) {
            val on = (i + 0.5f) / segments <= value
            drawRect(
                if (on) Fp.Pink else Fp.Pink.copy(alpha = 0.2f),
                topLeft = Offset(i * (w + gap), cy - h / 2f),
                size = Size(w, h),
            )
        }
    }
}
