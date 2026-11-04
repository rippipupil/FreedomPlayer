package com.freedomplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.freedomplayer.core.GhostAccessory
import com.freedomplayer.core.GhostSkin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Las caras del fantasmita (tablero Ghost). Las rosas son para lo bueno y las blancas (triste,
 * sorpresa y dormido) para errores, sin resultados y pausa. [Buscando] (@_@, ojos de remolino) es
 * la de mientras busca: con [SearchingGhost] los remolinos giran.
 */
enum class Mood { Feliz, Contento, Owo, Uwu, Guino, Sorpresa, Dormido, Triste, Buscando }

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

    /** Remolino de dos vueltas, centrado en (0, 0) y de radio 5 (se coloca en cada ojo). */
    val swirl by lazy {
        Path().apply {
            val turns = 2.2f
            val steps = 44
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val a = t * turns * 2f * PI.toFloat()
                val r = 0.4f + t * 4.6f
                val x = r * cos(a)
                val y = r * sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
    }
    val smallO by lazy { svgPath("M30 35 A2 2.4 0 1 0 34 35 A2 2.4 0 1 0 30 35 Z") }

    // «z z» del dormido
    val zBig by lazy { svgPath("M47 6 H54 L47 13 H54") }
    val zSmall by lazy { svgPath("M55 15 H59 L55 19 H59") }

    // Accesorios de los fantasmas coleccionables (los sombreros asoman por encima de la cabeza).
    val crown by lazy { svgPath("M21 11 L20 -1 L26.5 4.5 L32 -5 L37.5 4.5 L44 -1 L43 11 Q32 13.5 21 11 Z") }
    val partyHat by lazy { svgPath("M23 10 Q32 13 41 9 L34 -6 Z") }
    val partyStripes by lazy { svgPath("M25.6 4.8 Q31 6.6 38.4 3.6 M28.8 -0.8 Q32 0.2 36 -1.4") }
    val nightcap by lazy { svgPath("M17 13 Q20 1 33 -1 Q46 -2 52 9 Q55 14 56 21 L51.5 13 Q46 8 38 8.5 Q26 9.5 17 13 Z") }
    val nightcapBrim by lazy { svgPath("M16.5 13.5 Q30 6.5 46 9.5") }
    val headband by lazy { svgPath("M12.5 27 C12.5 -6 51.5 -6 51.5 27") }
}

private val PinkBody = Color(0xFFF4A6CB)
private val WhiteBody = Color(0xFFF3EEF4)
private val PinkInk = Color(0xFFC2326F)
private val WhiteInk = Color(0xFF1A1420)
private val PinkCheek = Color(0xFFFF6FAE)

/**
 * El fantasma que se lleva puesto (el coleccionable elegido). [Ghost], [SearchingGhost] y los
 * demás fantasmas de la app lo leen de aquí.
 */
val LocalGhostSkin = compositionLocalOf { GhostSkin.Clasico }

private val Gold = Color(0xFFFFD27A)
private val GoldInk = Color(0xFF7A4A00)
private val Cyan = Color(0xFF7CF5FF)
private val Magenta = Color(0xFFFF4FD8)
private val Shade = Color(0xFF14101C)

private fun GhostAccessory.isHat(): Boolean =
    this == GhostAccessory.Crown || this == GhostAccessory.Halo || this == GhostAccessory.PartyHat || this == GhostAccessory.Nightcap

private fun isWhite(mood: Mood, tint: GhostTint): Boolean =
    tint == GhostTint.Blanco || (tint == GhostTint.Auto && (mood == Mood.Triste || mood == Mood.Sorpresa || mood == Mood.Dormido))

/**
 * Dibuja el fantasma ocupando el lienzo (el SVG original mide 64×64). [spin] gira los remolinos
 * de [Mood.Buscando] (en grados). [skin] le cambia los colores (solo a las caras rosas: las blancas
 * siguen siendo blancas, que avisan de algo) y le pone su accesorio; si es un sombrero, el
 * fantasma se encoge un poco para que quepa.
 */
fun DrawScope.drawGhost(mood: Mood, tint: GhostTint = GhostTint.Auto, spin: Float = 0f, skin: GhostSkin = GhostSkin.Clasico) {
    val white = isWhite(mood, tint)
    val body = if (white) WhiteBody else skin.body?.let { Color(it) } ?: PinkBody
    val ink = if (white) WhiteInk else skin.ink?.let { Color(it) } ?: PinkInk
    val cheek = if (white) PinkBody else skin.cheek?.let { Color(it) } ?: PinkCheek
    val unit = this.size.minDimension / 64f
    val fit = if (skin.accessory.isHat()) HAT_FIT else 1f

    fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float = 1f) =
        drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), alpha = alpha)

    withTransform({
        // Encogido hacia los pies (32, 60): la cabeza baja y deja sitio al sombrero.
        translate(32f * (1f - fit) * unit, 60f * (1f - fit) * unit)
        scale(unit * fit, unit * fit, pivot = Offset.Zero)
    }) {
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
                drawPath(GhostArt.zBig, skin.body?.let { Color(it) } ?: PinkBody, style = line(2f))
                drawPath(GhostArt.zSmall, skin.body?.let { Color(it) } ?: PinkBody, style = line(1.6f))
            }
            Mood.Buscando -> {
                // Remolinos que giran en sentidos contrarios (mareado de tanto buscar) y una «o».
                translate(23f, 27f) { rotate(spin, pivot = Offset.Zero) { drawPath(GhostArt.swirl, ink, style = line(1.8f)) } }
                translate(41f, 27f) { rotate(-spin, pivot = Offset.Zero) { drawPath(GhostArt.swirl, ink, style = line(1.8f)) } }
                drawPath(GhostArt.smallO, ink)
            }
            Mood.Triste -> {
                rotate(-22f, pivot = Offset(24f, 27f)) { oval(24f, 27f, 4.6f, 2.6f, ink) }
                rotate(22f, pivot = Offset(40f, 27f)) { oval(40f, 27f, 4.6f, 2.6f, ink) }
                oval(32f, 37f, 2.2f, 3f, ink)
            }
        }
        drawAccessory(skin.accessory)
    }
}

/** Con sombrero, el fantasma se dibuja a este tamaño (encogido hacia los pies) para que quepa. */
private const val HAT_FIT = 0.88f

/** El accesorio, en las coordenadas del fantasma (64×64). */
private fun DrawScope.drawAccessory(accessory: GhostAccessory) {
    fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (accessory) {
        GhostAccessory.None -> Unit
        GhostAccessory.Crown -> {
            drawPath(GhostArt.crown, Gold)
            drawPath(GhostArt.crown, GoldInk, style = line(1.3f))
            drawCircle(PinkCheek, radius = 1.8f, center = Offset(32f, 6f))
            drawCircle(Cyan, radius = 1.2f, center = Offset(25f, 8f))
            drawCircle(Cyan, radius = 1.2f, center = Offset(39f, 8f))
        }
        GhostAccessory.Halo -> {
            val box = Size(24f, 6.8f)
            val at = Offset(20f, -4.4f)
            drawOval(Gold, topLeft = at, size = box, style = Stroke(width = 5f), alpha = 0.3f)
            drawOval(Color(0xFFFFE89A), topLeft = at, size = box, style = Stroke(width = 2.2f))
        }
        GhostAccessory.PartyHat -> {
            drawPath(GhostArt.partyHat, Cyan)
            drawPath(GhostArt.partyHat, Color(0xFF1E5C70), style = line(1.2f))
            drawPath(GhostArt.partyStripes, PinkCheek, style = Stroke(width = 2f))
            drawCircle(Gold, radius = 2.6f, center = Offset(34f, -6.5f))
        }
        GhostAccessory.Nightcap -> {
            drawPath(GhostArt.nightcap, Color(0xFF5B57D9))
            drawPath(GhostArt.nightcap, Color(0xFF1E1A4A), style = line(1.2f))
            drawPath(GhostArt.nightcapBrim, WhiteBody, style = line(4f))
            drawCircle(WhiteBody, radius = 3f, center = Offset(56f, 21.5f))
        }
        GhostAccessory.Headphones -> {
            drawPath(GhostArt.headband, Color(0xFFB8329A), style = line(3f))
            for (x in listOf(7.5f, 47.5f)) {
                val r = CornerRadius(3.5f)
                drawRoundRect(Magenta, topLeft = Offset(x, 21f), size = Size(9f, 14f), cornerRadius = r)
                drawRoundRect(Color(0xFF2A1F3D), topLeft = Offset(x, 21f), size = Size(9f, 14f), cornerRadius = r, style = Stroke(width = 1.2f))
            }
        }
        GhostAccessory.Shades -> {
            for (x in listOf(16.5f, 34.5f)) {
                drawRoundRect(Shade, topLeft = Offset(x, 22.5f), size = Size(13f, 8f), cornerRadius = CornerRadius(3f))
                drawLine(Color.White.copy(alpha = 0.7f), Offset(x + 2.5f, 24.8f), Offset(x + 5.5f, 24.8f), strokeWidth = 1.2f, cap = StrokeCap.Round)
            }
            drawLine(Shade, Offset(29.5f, 25f), Offset(34.5f, 25f), strokeWidth = 1.8f)
        }
    }
}

/**
 * El fantasmita. Es decorativo (no lleva etiqueta): el texto de al lado ya dice lo que pasa.
 * Si cambia de cara, cambia con un fundido corto.
 */
@Composable
fun Ghost(mood: Mood, size: Dp, modifier: Modifier = Modifier, tint: GhostTint = GhostTint.Auto, skin: GhostSkin = LocalGhostSkin.current) {
    Crossfade(targetState = mood, modifier = modifier.size(size), animationSpec = tween(Motion.STANDARD), label = "ghostMood") { m ->
        Canvas(Modifier.size(size)) { drawGhost(m, tint, skin = skin) }
    }
}

/**
 * El fantasmita buscando: ojos de remolino que giran y un vaivén suave, para mientras se busca o
 * se carga algo. Quieto (con los remolinos parados) si se reduce el movimiento.
 */
@Composable
fun SearchingGhost(size: Dp, modifier: Modifier = Modifier) {
    val skin = LocalGhostSkin.current
    if (LocalReduceMotion.current) {
        Canvas(modifier.size(size)) { drawGhost(Mood.Buscando, skin = skin) }
        return
    }
    val t = rememberInfiniteTransition(label = "searching")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "swirl")
    val sway by t.animateFloat(-7f, 7f, infiniteRepeatable(tween(900, easing = Motion.EaseInOut), RepeatMode.Reverse), label = "sway")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = Motion.EaseInOut), RepeatMode.Reverse), label = "bob")
    val lift = with(LocalDensity.current) { size.toPx() } * 0.08f
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = sway
                translationY = -bob * lift
            },
    ) { drawGhost(Mood.Buscando, spin = spin, skin = skin) }
}

/** El fantasma como Painter: icono de la ventana y de la bandeja en el PC. */
class GhostPainter(
    private val mood: Mood = Mood.Feliz,
    private val tint: GhostTint = GhostTint.Auto,
    private val skin: GhostSkin = GhostSkin.Clasico,
) : Painter() {
    override val intrinsicSize: Size get() = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        drawGhost(mood, tint, skin = skin)
    }
}
