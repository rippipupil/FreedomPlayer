package com.freedomplayer.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * true si el sistema pide reducir el movimiento. Entonces no hay animaciones decorativas
 * (fantasmas que flotan, parpadeo, saltitos) y las transiciones pasan a ser fundidos cortos.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Duraciones y curvas de toda la app: rápidas (150–300 ms) y con la curva estándar. */
object Motion {
    const val QUICK = 150
    const val STANDARD = 220
    const val SLOW = 300

    /** Curva estándar de Material (entra rápido, frena al final). */
    val Standard: Easing = FastOutSlowInEasing

    /** ease-in-out de CSS: la de los fantasmas que flotan. */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** ease-out de CSS: la de los paneles y el aviso del diseño. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
}

/** Animación corta con la curva estándar; sin animación si se pide reducir el movimiento. */
@Composable
fun <T> fpTween(durationMillis: Int = Motion.STANDARD, delayMillis: Int = 0, easing: Easing = Motion.Standard): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else tween(durationMillis, delayMillis, easing)

/** Entrada de avisos y elementos que suben un poco (16 dp) mientras aparecen. */
@Composable
fun riseIn(distance: Dp = 16.dp, durationMillis: Int = 250): EnterTransition {
    if (LocalReduceMotion.current) return fadeIn(tween(Motion.QUICK))
    val px = with(LocalDensity.current) { distance.roundToPx() }
    return fadeIn(tween(durationMillis, easing = Motion.EaseOut)) +
        slideInVertically(tween(durationMillis, easing = Motion.EaseOut)) { px }
}

@Composable
fun sinkOut(distance: Dp = 16.dp, durationMillis: Int = 180): ExitTransition {
    if (LocalReduceMotion.current) return fadeOut(tween(Motion.QUICK))
    val px = with(LocalDensity.current) { distance.roundToPx() }
    return fadeOut(tween(durationMillis, easing = Motion.Standard)) +
        slideOutVertically(tween(durationMillis, easing = Motion.Standard)) { px }
}

@Composable
fun quickFadeIn(durationMillis: Int = Motion.STANDARD): EnterTransition = fadeIn(tween(durationMillis, easing = Motion.Standard))

@Composable
fun quickFadeOut(durationMillis: Int = Motion.QUICK): ExitTransition = fadeOut(tween(durationMillis, easing = Motion.Standard))

/**
 * Clic con respuesta al pulsar: el elemento se encoge un poco (o se apaga un poco si se pide
 * reducir el movimiento) y en el PC el puntero pasa a ser una mano. Va al principio de la cadena
 * de modificadores para que el encogido afecte también al fondo y al borde.
 */
@Composable
fun Modifier.fpClickable(
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = tween(if (pressed) 90 else 180, easing = Motion.Standard),
        label = "pressScale",
    )
    val dim by animateFloatAsState(
        targetValue = if (pressed && reduce) 0.7f else 1f,
        animationSpec = tween(90),
        label = "pressDim",
    )
    return this
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = dim
        }
}

/** Flotar arriba y abajo (fpFloat del diseño: 4 px, 2,4 s, ease-in-out). */
@Composable
fun Modifier.fpFloat(amplitude: Dp = 4.dp, periodMillis: Int = 2400, delayMillis: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    val transition = rememberInfiniteTransition(label = "float")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis / 2, easing = Motion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "floatProgress",
    )
    val px = with(LocalDensity.current) { amplitude.toPx() }
    return this.graphicsLayer { translationY = -px * p }
}

/** Balanceo de los fantasmas blancos (fpBob del diseño: sube 3 px y gira de -4° a 4° en 3 s). */
@Composable
fun Modifier.fpBob(delayMillis: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    val transition = rememberInfiniteTransition(label = "bob")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = Motion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "bobProgress",
    )
    val px = with(LocalDensity.current) { 3.dp.toPx() }
    return this.graphicsLayer {
        translationY = -px * p
        rotationZ = -4f + 8f * p
    }
}

/**
 * Saltito de reacción: cada vez que cambia [key] (play, pausa, añadir, puntuar…) el fantasma
 * da un brinco de unos 300 ms. La primera vez no salta.
 */
@Composable
fun Modifier.fpHop(key: Any?, height: Dp = 6.dp): Modifier {
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(0f) }
    var last by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key == last) return@LaunchedEffect
        last = key
        if (reduce) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(1f, tween(130, easing = Motion.EaseOut))
        anim.animateTo(0f, tween(170, easing = Motion.EaseInOut))
    }
    val px = with(LocalDensity.current) { height.toPx() }
    return this.graphicsLayer {
        val v = anim.value
        translationY = -px * v
        scaleX = 1f + 0.06f * v
        scaleY = 1f + 0.06f * v
    }
}

/**
 * Fantasma que se asoma por el borde de un panel recién abierto (fpPeekL/fpPeekR del diseño):
 * llega desde [fromX] girado [rotation] grados.
 */
@Composable
fun Modifier.fpPeek(fromX: Dp, rotation: Float, delayMillis: Int = 90): Modifier {
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) anim.animateTo(1f, tween(Motion.SLOW, delayMillis, Motion.EaseOut))
    }
    val px = with(LocalDensity.current) { fromX.toPx() }
    return this.graphicsLayer {
        translationX = px * (1f - anim.value)
        rotationZ = rotation
        alpha = anim.value.coerceIn(0f, 1f)
    }
}

/**
 * Movimiento de las filas de las listas: aparecen con un fundido, se recolocan deslizando
 * (al puntuar, al reordenar) y se van con un fundido. Sin desplazamientos si se pide reducir
 * el movimiento.
 */
@Composable
fun LazyItemScope.itemMotion(): Modifier =
    if (LocalReduceMotion.current) {
        Modifier.animateItem(fadeInSpec = tween(Motion.QUICK), placementSpec = null, fadeOutSpec = tween(Motion.QUICK))
    } else {
        Modifier.animateItem(
            fadeInSpec = tween(Motion.STANDARD, easing = Motion.Standard),
            placementSpec = tween(Motion.SLOW, easing = Motion.Standard),
            fadeOutSpec = tween(Motion.QUICK, easing = Motion.Standard),
        )
    }
