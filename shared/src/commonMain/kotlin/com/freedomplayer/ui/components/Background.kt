package com.freedomplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

/**
 * Un rectángulo del SVG del diseño: x, y, ancho, alto, color y opacidad. No se puede llamar «R»:
 * Android quita de las librerías las clases con ese nombre (las toma por las de recursos).
 */
private class Block(val x: Float, val y: Float, val w: Float, val h: Float, val color: Long, val alpha: Float = 1f)

/** Edificios y ventanas del callejón del móvil (SVG de 390×300 pegado abajo). */
private val MobileBuildings = listOf(
    Block(0f, 70f, 70f, 230f, 0xFF1A1328), Block(62f, 120f, 58f, 180f, 0xFF160F22), Block(112f, 40f, 84f, 260f, 0xFF1C1430),
    Block(190f, 100f, 64f, 200f, 0xFF150F21), Block(248f, 60f, 78f, 240f, 0xFF1A1328), Block(318f, 130f, 72f, 170f, 0xFF160F22),
    Block(12f, 92f, 7f, 9f, 0xFFFF3FA4, 0.55f), Block(30f, 92f, 7f, 9f, 0xFF5B57D9, 0.5f), Block(12f, 120f, 7f, 9f, 0xFFFF3FA4, 0.25f),
    Block(48f, 148f, 7f, 9f, 0xFFF4A6CB, 0.45f), Block(126f, 60f, 8f, 10f, 0xFF5B57D9, 0.55f), Block(146f, 60f, 8f, 10f, 0xFFFF3FA4, 0.6f),
    Block(166f, 86f, 8f, 10f, 0xFF2F9C82, 0.5f), Block(126f, 112f, 8f, 10f, 0xFFFF3FA4, 0.3f), Block(166f, 138f, 8f, 10f, 0xFFF4A6CB, 0.4f),
    Block(262f, 82f, 8f, 10f, 0xFFFF3FA4, 0.5f), Block(282f, 82f, 8f, 10f, 0xFFFF3FA4, 0.2f), Block(302f, 108f, 8f, 10f, 0xFF5B57D9, 0.55f),
    Block(262f, 134f, 8f, 10f, 0xFF2F9C82, 0.45f), Block(334f, 152f, 8f, 10f, 0xFFF4A6CB, 0.35f), Block(354f, 178f, 8f, 10f, 0xFFFF3FA4, 0.45f),
    Block(210f, 122f, 22f, 7f, 0xFFFF3FA4, 0.7f), Block(0f, 292f, 390f, 8f, 0xFF0D0915),
)

/** Edificios y ventanas del callejón del PC (SVG de 1280×260 pegado abajo). */
private val PcBuildings = listOf(
    Block(0f, 80f, 120f, 180f, 0xFF1A1328), Block(110f, 130f, 90f, 130f, 0xFF160F22), Block(196f, 50f, 130f, 210f, 0xFF1C1430),
    Block(320f, 110f, 100f, 150f, 0xFF150F21), Block(414f, 70f, 120f, 190f, 0xFF1A1328), Block(530f, 140f, 110f, 120f, 0xFF160F22),
    Block(636f, 40f, 140f, 220f, 0xFF1C1430), Block(770f, 120f, 96f, 140f, 0xFF150F21), Block(860f, 80f, 124f, 180f, 0xFF1A1328),
    Block(980f, 130f, 110f, 130f, 0xFF160F22), Block(1086f, 60f, 120f, 200f, 0xFF1C1430), Block(1200f, 110f, 80f, 150f, 0xFF150F21),
    Block(20f, 104f, 9f, 11f, 0xFFFF3FA4, 0.5f), Block(44f, 104f, 9f, 11f, 0xFF5B57D9, 0.5f), Block(216f, 74f, 9f, 11f, 0xFFFF3FA4, 0.55f),
    Block(240f, 102f, 9f, 11f, 0xFF2F9C82, 0.45f), Block(290f, 74f, 9f, 11f, 0xFFF4A6CB, 0.4f), Block(436f, 96f, 9f, 11f, 0xFF5B57D9, 0.55f),
    Block(476f, 124f, 9f, 11f, 0xFFFF3FA4, 0.35f), Block(660f, 64f, 9f, 11f, 0xFFFF3FA4, 0.6f), Block(684f, 64f, 9f, 11f, 0xFFFF3FA4, 0.25f),
    Block(720f, 92f, 9f, 11f, 0xFF2F9C82, 0.5f), Block(690f, 120f, 30f, 8f, 0xFFFF3FA4, 0.7f), Block(880f, 104f, 9f, 11f, 0xFFF4A6CB, 0.45f),
    Block(930f, 132f, 9f, 11f, 0xFF5B57D9, 0.5f), Block(1106f, 84f, 9f, 11f, 0xFFFF3FA4, 0.5f), Block(1150f, 112f, 9f, 11f, 0xFF2F9C82, 0.45f),
)

/** En el PC, el fondo acaba encima de la barra del reproductor; el diseño lo mide desde abajo de la ventana. */
private const val PcBarHeight = 90f

private val Neon = Color(0xFFFF3FA4)
private val Pink = Color(0xFFF4A6CB)
private val Violet = Color(0xFF5B57D9)
private val Lamp = Color(0xFFFFE3F1)
private val Pole = Color(0xFF1B1428)

private val circuitMain by lazy { svgPath("M58 0 V40 L44 54 V170 L52 178 V300") }
private val circuitViolet by lazy { svgPath("M50 20 V60 L38 72 V120") }
private val diamondA by lazy { svgPath("M44 96 L41 101 L44 106 L47 101 Z") }
private val diamondB by lazy { svgPath("M52 236 L49 241 L52 246 L55 241 Z") }

/** Grano fino del fondo (el feTurbulence del diseño): una baldosa de ruido que se repite. */
private val grainTile: ImageBitmap by lazy {
    val n = 96
    val bitmap = ImageBitmap(n, n)
    val canvas = GraphicsCanvas(bitmap)
    val paint = Paint()
    val random = Random(7)
    for (y in 0 until n) {
        for (x in 0 until n) {
            val v = random.nextFloat()
            paint.color = Color(v, v, v, 1f)
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }
    }
    bitmap
}

private val grainBrush: ShaderBrush by lazy { ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated)) }

private val NoFlicker: State<Float> = mutableFloatStateOf(1f)

/**
 * El fondo «callejón»: halo rosa, farola con su cono de luz, edificios con ventanitas, circuitos
 * en el borde derecho (móvil), estrellitas, un fantasma dormido casi transparente y grano.
 *
 * - [flicker]: la farola parpadea y el fantasma se mueve (solo en Inicio, y nunca si el sistema
 *   pide reducir el movimiento).
 * - [tint]: brillo del color de la carátula detrás (solo el Reproductor).
 */
@Composable
fun AlleyBackground(modifier: Modifier = Modifier, wide: Boolean, flicker: Boolean, tint: Color? = null) {
    val reduce = LocalReduceMotion.current
    val animate = flicker && !reduce
    val glow: State<Float>
    val drift: State<Float>
    if (animate) {
        val transition = rememberInfiniteTransition(label = "alley")
        glow = transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 7000
                    1f at 0
                    1f at 3220
                    0.55f at 3360
                    1f at 3500
                    0.85f at 5810
                    1f at 7000
                },
            ),
            label = "flicker",
        )
        drift = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4500, easing = Motion.EaseInOut), RepeatMode.Reverse),
            label = "drift",
        )
    } else {
        glow = NoFlicker
        drift = remember { mutableFloatStateOf(0f) }
    }
    val tintColor by animateColorAsState(tint ?: Color(0xFF7A1F55), tween(Motion.SLOW), label = "tint")
    val tintAlpha by animateFloatAsState(if (tint != null) 0.55f else 0f, tween(Motion.SLOW), label = "tintAlpha")

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            if (tintAlpha > 0.01f) drawTint(tintColor, tintAlpha, d)
            if (wide) drawPcAlley(glow.value, d) else drawMobileAlley(glow.value, d)
            drawRect(grainBrush, alpha = if (wide) 0.08f else 0.09f, blendMode = BlendMode.Overlay)
        }
        // Fantasma dormido del fondo, al 16 %.
        val ghostSize = if (wide) 36.dp else 30.dp
        val dens = LocalDensity.current
        // Sitio del diseño (left 140 / bottom 210 en el móvil; left 560 / bottom 150 en el PC), a escala del ancho.
        val ghostX = w * (if (wide) 560f / 1280f else 140f / 390f)
        val ghostY = with(dens) { h - (if (wide) (150f - PcBarHeight).dp else 210.dp).toPx() - ghostSize.toPx() }
        Box(
            Modifier
                .offset { IntOffset(ghostX.toInt(), ghostY.toInt()) }
                .graphicsLayer {
                    alpha = 0.16f
                    translationX = 6.dp.toPx() * drift.value
                    translationY = -8.dp.toPx() * drift.value
                },
        ) {
            Ghost(Mood.Dormido, ghostSize)
        }
    }
}

private fun DrawScope.drawTint(color: Color, alpha: Float, d: Float) {
    // Elipse de left -60 a right -60, top 120, alto 520, con degradado «closest-side».
    val left = -60f * d
    val width = this.size.width + 120f * d
    val top = 120f * d
    val height = 520f * d
    val center = Offset(left + width / 2f, top + height / 2f)
    val rx = width / 2f
    scale(1f, (height / 2f) / rx, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = rx),
            radius = rx,
            center = center,
            alpha = alpha,
        )
    }
}

/**
 * El brillo de la farola como el box-shadow del diseño: un disco desenfocado (gaussiana con
 * sigma = blur / 2), no un degradado lineal, que se veía el doble de fuerte. [profile] va del
 * centro al borde, con el centro a 1.
 */
private fun DrawScope.glowDot(center: Offset, radius: Float, color: Color, peak: Float, profile: FloatArray) {
    val stops = Array(profile.size) { i -> (i / (profile.size - 1f)) to color.copy(alpha = (peak * profile[i]).coerceIn(0f, 1f)) }
    drawCircle(
        brush = Brush.radialGradient(*stops, center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

/** Perfil del brillo grande (spread ≈ blur / 2) y del pequeño (spread ≈ blur / 3). */
private val OuterGlow = floatArrayOf(1f, 0.947f, 0.802f, 0.608f, 0.41f, 0.247f, 0.132f, 0.062f, 0.026f)
private val InnerGlow = floatArrayOf(1f, 0.963f, 0.854f, 0.679f, 0.47f, 0.276f, 0.134f, 0.054f, 0.017f)

private fun DrawScope.sparkle(x: Float, y: Float, px: Float, color: Color, alpha: Float) {
    translate(x, y) {
        scale(px / 24f, px / 24f, pivot = Offset.Zero) {
            drawPath(FpIcons.Sparkle.fillPath ?: Path(), color, alpha = alpha)
        }
    }
}

private fun DrawScope.drawMobileAlley(glow: Float, d: Float) {
    val w = this.size.width
    val h = this.size.height
    val sx = w / (390f * d)

    // Halo rosa arriba a la derecha (460 px, right -120, top -150).
    val haloC = Offset(w + 120f * d - 230f * d, -150f * d + 230f * d)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Neon.copy(alpha = 0.42f),
            0.55f to Neon.copy(alpha = 0.12f),
            1f to Neon.copy(alpha = 0f),
            center = haloC,
            radius = 230f * d,
        ),
        radius = 230f * d,
        center = haloC,
        alpha = glow,
    )

    // Cono de luz: caja de 300×620 en right 52, top 30, girada 8° desde su esquina de arriba a la derecha.
    val pivot = Offset(w - 52f * d, 30f * d)
    translate(pivot.x, pivot.y) {
        rotate(8f, pivot = Offset.Zero) {
            val cone = Path().apply {
                moveTo(-66f * d, 0f)
                lineTo(-24f * d, 0f)
                lineTo(0f, 620f * d)
                lineTo(-300f * d, 620f * d)
                close()
            }
            drawPath(
                cone,
                brush = Brush.verticalGradient(
                    0f to Color(0xFFFF8CC6).copy(alpha = 0.22f),
                    0.85f to Neon.copy(alpha = 0f),
                    startY = 0f,
                    endY = 620f * d,
                ),
                alpha = glow,
            )
        }
    }

    // Poste y farola.
    drawRect(Pole, topLeft = Offset(w - 85f * d, 46f * d), size = Size(4f * d, h - 46f * d))
    val lamp = Offset(w - 83f * d, 35f * d)
    // box-shadow: 0 0 18px 6px rgba(255,180,220,.8), 0 0 60px 20px rgba(255,63,164,.5)
    glowDot(lamp, 93f * d, Neon, 0.227f * glow, OuterGlow)
    glowDot(lamp, 37f * d, Color(0xFFFFB4DC), 0.714f * glow, InnerGlow)
    drawCircle(Lamp, radius = 13f * d, center = lamp, alpha = 0.6f + 0.4f * glow)

    // Edificios (SVG de 390×300 pegado abajo, estirado al ancho).
    translate(0f, h - 300f * d) {
        for (r in MobileBuildings) {
            drawRect(Color(r.color), topLeft = Offset(r.x * d * sx, r.y * d), size = Size(r.w * d * sx, r.h * d), alpha = r.alpha)
        }
    }

    // Circuitos del borde derecho (SVG de 60×300 en right 0, top 110).
    translate(w - 60f * d, 110f * d) {
        scale(d, d, pivot = Offset.Zero) {
            drawPath(circuitMain, Neon, alpha = 0.45f, style = Stroke(width = 1f, cap = StrokeCap.Butt))
            drawPath(circuitViolet, Violet, alpha = 0.55f, style = Stroke(width = 1f, cap = StrokeCap.Butt))
            drawCircle(Violet, radius = 2.5f, center = Offset(38f, 122f))
            drawPath(diamondA, Neon)
            drawPath(diamondB, Neon, alpha = 0.7f)
        }
    }

    // Estrellitas.
    sparkle(22f * d * sx, 210f * d, 14f * d, Neon, 0.8f)
    sparkle(352f * d * sx, 470f * d, 9f * d, Pink, 0.6f)
    sparkle(160f * d * sx, 24f * d, 7f * d, Pink, 0.5f)
}

private fun DrawScope.drawPcAlley(glow: Float, d: Float) {
    val w = this.size.width
    val h = this.size.height
    val sx = w / (1280f * d)

    // Halo (700 px en right 120, top -260).
    val haloC = Offset(w - 120f * d - 350f * d, -260f * d + 350f * d)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Neon.copy(alpha = 0.36f),
            0.55f to Neon.copy(alpha = 0.1f),
            1f to Neon.copy(alpha = 0f),
            center = haloC,
            radius = 350f * d,
        ),
        radius = 350f * d,
        center = haloC,
        alpha = glow,
    )

    // Cono de luz: caja de 420×760 en right 330, top 40, sin girar.
    val left = w - 330f * d - 420f * d
    val top = 40f * d
    val cone = Path().apply {
        moveTo(left + 294f * d, top)
        lineTo(left + 344.4f * d, top)
        lineTo(left + 420f * d, top + 760f * d)
        lineTo(left, top + 760f * d)
        close()
    }
    drawPath(
        cone,
        brush = Brush.verticalGradient(
            0f to Color(0xFFFF8CC6).copy(alpha = 0.16f),
            0.8f to Neon.copy(alpha = 0f),
            startY = top,
            endY = top + 760f * d,
        ),
        alpha = glow,
    )

    // Poste y farola.
    drawRect(Pole, topLeft = Offset(w - 457f * d, 62f * d), size = Size(4f * d, h - 62f * d))
    val lamp = Offset(w - 455f * d, 49f * d)
    // box-shadow: 0 0 22px 8px rgba(255,180,220,.8), 0 0 80px 26px rgba(255,63,164,.45)
    glowDot(lamp, 121f * d, Neon, 0.184f * glow, OuterGlow)
    glowDot(lamp, 45f * d, Color(0xFFFFB4DC), 0.71f * glow, InnerGlow)
    drawCircle(Lamp, radius = 15f * d, center = lamp, alpha = 0.6f + 0.4f * glow)

    // Edificios (SVG de 1280×260 pegado abajo de la ventana, estirado al ancho): la barra del
    // reproductor (90 px) tapa su parte de abajo, como en el diseño.
    translate(0f, h + PcBarHeight * d - 260f * d) {
        for (r in PcBuildings) {
            drawRect(Color(r.color), topLeft = Offset(r.x * d * sx, r.y * d), size = Size(r.w * d * sx, r.h * d), alpha = r.alpha)
        }
    }

    sparkle(90f * d * sx, 150f * d, 16f * d, Neon, 0.7f)
    sparkle(1180f * d * sx, 380f * d, 10f * d, Pink, 0.6f)
}
