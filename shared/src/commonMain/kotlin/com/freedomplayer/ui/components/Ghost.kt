package com.freedomplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp

/**
 * Las 8 caras del fantasmita (tablero Ghost). Las rosas son para lo bueno y las blancas
 * (triste, sorpresa y dormido) para errores, sin resultados y pausa.
 */
enum class Mood { Feliz, Contento, Owo, Uwu, Guino, Sorpresa, Dormido, Triste }

/** Color del fantasma: el de su cara o forzado a rosa o blanco. */
enum class GhostTint { Auto, Rosa, Blanco }

private object GhostArt {
    val body: Path by lazy {
        svgPath(
            "M32 6 C18 6 12 17 12 29 L12 33 C8 34 5 37 4 40 C8 40 11 39 12 38 L12 50 C15 54 18 56 21 52 " +
                "C24 57 28 58 32 53 C36 58 40 57 43 52 C46 56 49 54 52 50 L52 38 C53 39 56 40 60 40 " +
                "C59 37 56 34 52 33 L52 29 C52 17 46 6 32 6 Z",
        )
    }

    // Ojos
    val happyEyes by lazy { svgPath("M20 23 L26 27 L20 31 M44 23 L38 27 L44 31") }
    val arcEyes by lazy { svgPath("M19.5 28.5 L23 24.5 L26.5 28.5 M37.5 28.5 L41 24.5 L44.5 28.5") }
    val arcEyeLeft by lazy { svgPath("M19.5 28.5 L23 24.5 L26.5 28.5") }
    val wink by lazy { svgPath("M37.5 27.5 Q39.5 24.5 41 27 Q42.5 29.5 44.5 26.5") }
    val uwuEyes by lazy { svgPath("M19.5 25 Q23 31 26.5 25 M37.5 25 Q41 31 44.5 25") }
    val sleepyEyes by lazy { svgPath("M19.5 27 Q23 29.5 26.5 27 M37.5 27 Q41 29.5 44.5 27") }

    // Bocas
    val smile by lazy { svgPath("M28 33 Q32 37 36 33") }
    val bigSmile by lazy { svgPath("M28 33 Q32 37.5 36 33") }
    val catMouth by lazy { svgPath("M27 33 Q29.5 37 32 33 Q34.5 37 37 33") }
    val catMouthLow by lazy { svgPath("M27 33.5 Q29.5 37.5 32 33.5 Q34.5 37.5 37 33.5") }
    val tongue by lazy { svgPath("M33.5 35.2 Q35 38.5 36.6 35.6") }

    // «z z» del dormido
    val zBig by lazy { svgPath("M47 6 H54 L47 13 H54") }
    val zSmall by lazy { svgPath("M55 15 H59 L55 19 H59") }
}

private val PinkBody = Color(0xFFF4A6CB)
private val WhiteBody = Color(0xFFF3EEF4)
private val PinkInk = Color(0xFFC2326F)
private val WhiteInk = Color(0xFF1A1420)
private val PinkCheek = Color(0xFFFF6FAE)

private fun isWhite(mood: Mood, tint: GhostTint): Boolean =
    tint == GhostTint.Blanco || (tint == GhostTint.Auto && (mood == Mood.Triste || mood == Mood.Sorpresa || mood == Mood.Dormido))

/** Dibuja el fantasma ocupando el lienzo (el SVG original mide 64×64). */
fun DrawScope.drawGhost(mood: Mood, tint: GhostTint = GhostTint.Auto) {
    val white = isWhite(mood, tint)
    val body = if (white) WhiteBody else PinkBody
    val ink = if (white) WhiteInk else PinkInk
    val cheek = if (white) PinkBody else PinkCheek
    val k = this.size.minDimension / 64f

    fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float = 1f) =
        drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), alpha = alpha)

    scale(k, k, pivot = Offset.Zero) {
        drawPath(GhostArt.body, body)
        oval(23f, 16f, 7f, 4f, Color.White, if (white) 0.6f else 0.35f)
        if (mood == Mood.Owo || mood == Mood.Uwu || mood == Mood.Guino) {
            oval(17.5f, 33f, 3.4f, 1.8f, cheek, 0.7f)
            oval(46.5f, 33f, 3.4f, 1.8f, cheek, 0.7f)
        }
        when (mood) {
            Mood.Feliz -> {
                drawPath(GhostArt.happyEyes, ink, style = line(2.6f))
                drawPath(GhostArt.smile, ink, style = line(2.6f))
            }
            Mood.Contento -> {
                drawPath(GhostArt.arcEyes, ink, style = line(2.6f))
                drawPath(GhostArt.bigSmile, ink, style = line(2.6f))
            }
            Mood.Owo -> {
                drawCircle(ink, radius = 3.6f, center = Offset(23f, 27f), style = Stroke(width = 2.4f))
                drawCircle(ink, radius = 3.6f, center = Offset(41f, 27f), style = Stroke(width = 2.4f))
                drawPath(GhostArt.catMouth, ink, style = line(2.4f))
            }
            Mood.Uwu -> {
                drawPath(GhostArt.uwuEyes, ink, style = line(2.6f))
                drawPath(GhostArt.catMouthLow, ink, style = line(2.4f))
            }
            Mood.Guino -> {
                drawPath(GhostArt.arcEyeLeft, ink, style = line(2.6f))
                drawPath(GhostArt.wink, ink, style = line(2.6f))
                drawPath(GhostArt.bigSmile, ink, style = line(2.6f))
                drawPath(GhostArt.tongue, PinkCheek)
            }
            Mood.Sorpresa -> {
                drawCircle(ink, radius = 3.2f, center = Offset(23.5f, 26.5f))
                drawCircle(ink, radius = 3.2f, center = Offset(40.5f, 26.5f))
                oval(32f, 36f, 3f, 4f, ink)
            }
            Mood.Dormido -> {
                drawPath(GhostArt.sleepyEyes, ink, style = line(2.4f))
                oval(32f, 35f, 1.8f, 2.2f, ink)
                drawPath(GhostArt.zBig, PinkBody, style = line(2f))
                drawPath(GhostArt.zSmall, PinkBody, style = line(1.6f))
            }
            Mood.Triste -> {
                rotate(-22f, pivot = Offset(24f, 27f)) { oval(24f, 27f, 4.6f, 2.6f, ink) }
                rotate(22f, pivot = Offset(40f, 27f)) { oval(40f, 27f, 4.6f, 2.6f, ink) }
                oval(32f, 37f, 2.2f, 3f, ink)
            }
        }
    }
}

/**
 * El fantasmita. Es decorativo (no lleva etiqueta): el texto de al lado ya dice lo que pasa.
 * Si cambia de cara, cambia con un fundido corto.
 */
@Composable
fun Ghost(mood: Mood, size: Dp, modifier: Modifier = Modifier, tint: GhostTint = GhostTint.Auto) {
    Crossfade(targetState = mood, modifier = modifier.size(size), animationSpec = tween(Motion.STANDARD), label = "ghostMood") { m ->
        Canvas(Modifier.size(size)) { drawGhost(m, tint) }
    }
}

/** El fantasma como Painter: icono de la ventana y de la bandeja en el PC. */
class GhostPainter(private val mood: Mood = Mood.Feliz, private val tint: GhostTint = GhostTint.Auto) : Painter() {
    override val intrinsicSize: Size get() = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        drawGhost(mood, tint)
    }
}
