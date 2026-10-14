package com.freedomplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Side
import com.freedomplayer.ui.theme.Fp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pestañas laterales del móvil (componente Pestanas): tiradores de 18×204 con forma de
 * trapecio, raya rosa en el lado interior e icono. La zona táctil mide 44 dp de ancho.
 * [top] es dónde empiezan (286 de 844 en el diseño).
 */
@Composable
fun SideTabs(state: AppState, top: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        SideTab(left = true, top = top) { state.openPanel(Side.Left) }
        SideTab(left = false, top = top) { state.openPanel(Side.Right) }
    }
}

@Composable
private fun BoxScope.SideTab(left: Boolean, top: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    // Al pulsar, el tirador sale un poco hacia dentro.
    val nudge by animateDpAsState(if (pressed && !reduce) 4.dp else 0.dp, tween(if (pressed) 90 else 180, easing = Motion.Standard), label = "tabNudge")
    Box(
        Modifier
            .align(if (left) Alignment.TopStart else Alignment.TopEnd)
            .offset(y = top)
            .size(44.dp, 204.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (left) "Abrir canciones" else "Abrir playlists" },
    ) {
        Box(
            Modifier
                .align(if (left) Alignment.TopStart else Alignment.TopEnd)
                .size(18.dp, 204.dp)
                .graphicsLayer { translationX = (if (left) 1f else -1f) * nudge.toPx() },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val d = density
                val shape = Path().apply {
                    if (left) {
                        moveTo(0f, 0f)
                        lineTo(18f * d, 16f * d)
                        lineTo(18f * d, 188f * d)
                        lineTo(0f, 204f * d)
                    } else {
                        moveTo(18f * d, 0f)
                        lineTo(0f, 16f * d)
                        lineTo(0f, 188f * d)
                        lineTo(18f * d, 204f * d)
                    }
                    close()
                }
                drawPath(shape, Fp.Tab)
                val x = if (left) 17.5f * d else 0.5f * d
                drawLine(Fp.Neon.copy(alpha = 0.7f), Offset(x, 16f * d), Offset(x, 188f * d), strokeWidth = 1f * d)
            }
            FpIconView(
                if (left) FpIcons.Note else FpIcons.Shelf,
                14.dp,
                Fp.Pink,
                Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd).offset(x = if (left) 2.dp else (-2).dp, y = 95.dp),
            )
        }
    }
}

/** Forma de la pestañita del PC (22×44, con los extremos en diagonal). */
private fun pcTabShape(left: Boolean) = GenericShape { size, _ ->
    val k = size.height * 6f / 44f
    if (left) {
        moveTo(0f, 0f)
        lineTo(size.width, k)
        lineTo(size.width, size.height - k)
        lineTo(0f, size.height)
    } else {
        moveTo(0f, k)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height - k)
    }
    close()
}

/**
 * Borde del PC (franja de 22 dp): con el ratón encima, una línea rosa crece desde el centro
 * durante 1 s y al completarse se abre el panel; si el ratón sale antes, se cancela.
 * La pestañita del centro abre el panel al momento con un clic.
 */
@Composable
fun EdgeZone(state: AppState, side: Side, modifier: Modifier = Modifier) {
    val left = side == Side.Left
    val reduce = LocalReduceMotion.current
    var hovered by remember { mutableStateOf(false) }
    val open = state.panel != null
    val grow = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(hovered, open) {
        if (hovered && !open) {
            shown.snapTo(1f)
            grow.snapTo(if (reduce) 1f else 0f)
            if (!reduce) launch { grow.animateTo(1f, tween(1000, easing = LinearEasing)) }
            delay(1000)
            hovered = false
            state.openPanel(side)
        } else {
            if (shown.value > 0f) shown.animateTo(0f, tween(Motion.QUICK))
            grow.snapTo(0f)
        }
    }
    Box(
        modifier
            .width(22.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val base = 2.dp.toPx()
            drawRect(Fp.Neon.copy(alpha = 0.25f), topLeft = Offset(if (left) 0f else w - base, 0f), size = Size(base, h))
            val a = shown.value
            if (a > 0f) {
                val lineH = h * grow.value
                val top = (h - lineH) / 2f
                val line = 3.dp.toPx()
                val glow = 10.dp.toPx()
                drawRect(
                    Fp.Neon.copy(alpha = 0.22f * a),
                    topLeft = Offset(if (left) 0f else w - glow, top),
                    size = Size(glow, lineH),
                )
                drawRect(Fp.Neon.copy(alpha = a), topLeft = Offset(if (left) 0f else w - line, top), size = Size(line, lineH))
            }
        }
        val shape = remember(left) { pcTabShape(left) }
        val hoverSource = remember { MutableInteractionSource() }
        val tabHovered by hoverSource.collectIsHoveredAsState()
        Box(
            Modifier
                .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                .size(22.dp, 44.dp)
                .fpClickable(onClickLabel = null) { state.openPanel(side) }
                .hoverable(hoverSource)
                .semantics { contentDescription = if (left) "Abrir canciones" else "Abrir playlists" }
                .clip(shape)
                .background(if (tabHovered) Fp.Panel else Fp.Tab),
            contentAlignment = Alignment.Center,
        ) {
            FpIconView(if (left) FpIcons.NoteBold else FpIcons.ShelfPc, 12.dp, Fp.Pink)
        }
    }
}
