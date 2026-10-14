package com.freedomplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import com.freedomplayer.core.Ranking
import com.freedomplayer.ui.theme.Fp
import kotlin.math.roundToInt

private val starPath: Path get() = FpIcons.Sparkle.fillPath ?: Path()

/** «3,5 de 5» para una nota de 7. */
fun starsLabel(score: Int?): String = if (score == null) "Sin nota" else "${Ranking.label(score)} de 5"

/**
 * Dibuja una ✦ de [px] en (x, y) rellena de 0 a 1 de izquierda a derecha.
 * Vacía: [offColor] (o solo el contorno si [outline]).
 */
private fun DrawScope.star(x: Float, y: Float, px: Float, fill: Float, onColor: Color, offColor: Color, outline: Boolean, pop: Float = 1f) {
    val k = px / 24f
    translate(x, y) {
        scale(pop, pop, pivot = Offset(px / 2f, px / 2f)) {
            scale(k, k, pivot = Offset.Zero) {
                if (outline) {
                    drawPath(starPath, offColor, style = Stroke(width = 1.4f))
                } else {
                    drawPath(starPath, offColor)
                }
                if (fill > 0f) {
                    clipRect(left = 0f, top = 0f, right = 24f * fill.coerceIn(0f, 1f), bottom = 24f) {
                        drawPath(starPath, onColor)
                    }
                }
            }
        }
    }
}

/**
 * La nota en 5 ✦ (cada una vale 2 puntos; un impar deja media). Si cambia la nota,
 * el relleno se desliza hasta la nueva.
 */
@Composable
fun StarsView(score: Int?, size: Dp, modifier: Modifier = Modifier, gap: Dp = 2.dp, onColor: Color = Fp.Neon, offColor: Color = Fp.StarOff) {
    val shown by animateFloatAsState((score ?: 0).toFloat(), fpTween(Motion.SLOW), label = "stars")
    Canvas(
        modifier
            .width(size * 5 + gap * 4)
            .height(size)
            .semantics { contentDescription = starsLabel(score) },
    ) {
        val px = size.toPx()
        val g = gap.toPx()
        for (i in 0 until 5) {
            val fill = ((shown - i * 2) / 2f).coerceIn(0f, 1f)
            star(i * (px + g), 0f, px, fill, onColor, offColor, outline = false)
        }
    }
}

/**
 * Para puntuar: 5 ✦ de [starSize] en casillas de [cell] (44 dp: zona táctil cómoda).
 * La mitad izquierda de cada ✦ da media estrella y la derecha la entera. Mientras el dedo está
 * apoyado se ve la nota que quedaría; se guarda al soltar. Se puede arrastrar para elegir.
 */
@Composable
fun StarsInput(
    score: Int?,
    onRate: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    cell: Dp = 44.dp,
    starSize: Dp = 26.dp,
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    var popIndex by remember { mutableIntStateOf(-1) }
    val pop = remember { Animatable(1f) }
    val rate by rememberUpdatedState(onRate)
    val reduce = LocalReduceMotion.current
    var popKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(popKey) {
        if (popKey == 0 || reduce) return@LaunchedEffect
        pop.snapTo(1f)
        pop.animateTo(1.28f, tween(90, easing = Motion.EaseOut))
        pop.animateTo(1f, tween(160, easing = Motion.Standard))
    }
    val shown = preview ?: score
    Canvas(
        modifier
            .width(cell * 5)
            .height(cell)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                fun scoreAt(x: Float): Int {
                    val w = size.width / 5f
                    val i = (x / w).toInt().coerceIn(0, 4)
                    val left = (x - i * w) < w / 2f
                    return Ranking.scoreFromTap(i, left)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    preview = scoreAt(down.position.x)
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id }
                        if (c == null) {
                            cancelled = true
                            break
                        }
                        if (!c.pressed) break
                        val y = c.position.y
                        if (y < -size.height || y > size.height * 2) {
                            cancelled = true
                            break
                        }
                        preview = scoreAt(c.position.x)
                        c.consume()
                    }
                    val chosen = preview
                    preview = null
                    if (!cancelled && chosen != null) {
                        popIndex = (chosen - 1) / 2
                        popKey++
                        rate(chosen)
                    }
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = starsLabel(score)
                progressBarRangeInfo = ProgressBarRangeInfo((score ?: 0).toFloat(), 0f..10f, steps = 9)
                setProgress { v ->
                    val s = v.roundToInt().coerceIn(1, 10)
                    rate(s)
                    true
                }
            },
    ) {
        val cellPx = cell.toPx()
        val px = starSize.toPx()
        val off = (cellPx - px) / 2f
        val value = (shown ?: 0).toFloat()
        for (i in 0 until 5) {
            val fill = ((value - i * 2) / 2f).coerceIn(0f, 1f)
            star(
                x = i * cellPx + off,
                y = off,
                px = px,
                fill = fill,
                onColor = if (preview != null) Fp.NeonSoft else Fp.Neon,
                offColor = Fp.StarLine,
                outline = true,
                pop = if (i == popIndex) pop.value else 1f,
            )
        }
    }
}
