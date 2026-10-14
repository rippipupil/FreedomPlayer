package com.freedomplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.downloadMessage
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.DownloadJob
import com.freedomplayer.ui.ErrorNotice
import com.freedomplayer.ui.Toast
import com.freedomplayer.ui.Tone
import com.freedomplayer.ui.theme.Fp

fun toneColor(tone: Tone): Color = when (tone) {
    Tone.Pink -> Fp.Pink
    Tone.Jade -> Fp.Jade
    Tone.Neon -> Fp.Neon
    Tone.Bone -> Fp.Bone
}

private fun Modifier.leftBar(color: Color, width: Dp): Modifier =
    this.drawBehind { drawRect(color, size = Size(width.toPx(), this.size.height)) }

/** El aviso del diseño (02b): fondo #241A38, raya a la izquierda, fantasma y texto. */
@Composable
fun ToastCard(toast: Toast, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Fp.ToastBg)
            .leftBar(toneColor(toast.tone), 3.dp)
            .padding(start = 14.dp, end = if (toast.actionLabel != null) 4.dp else 14.dp, top = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ghost(toast.mood, 30.dp, Modifier.fpFloat().fpHop(toast.id))
        Nunito(toast.text, 13.sp, Modifier.weight(1f), weight = FontWeight.ExtraBold, lineHeight = 18.sp)
        val label = toast.actionLabel
        if (label != null) TextAction(label, onAction, color = Fp.Neon, spacing = 1.5.sp)
    }
}

/** Descarga en curso: «¡Añadida! Descargando y buscando letra…» o «Descargando 3 de 12», con su barra. */
@Composable
fun DownloadCard(job: DownloadJob, online: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Fp.ToastBg)
            .leftBar(Fp.Pink, 3.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ghost(if (online) Mood.Owo else Mood.Dormido, 30.dp, Modifier.fpFloat())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val text = when {
                !online -> "En pausa: sin conexión"
                job.resolving -> "Buscando las canciones de «${job.title}»…"
                else -> downloadMessage(job.done, job.total)
            }
            Nunito(text, 13.sp, weight = FontWeight.ExtraBold, maxLines = 2)
            if (job.isCollection && !job.resolving) Nunito("«${job.title}»", 12.sp, color = Fp.Mist, maxLines = 1)
            SegmentBar(count = if (job.total > 1) job.total.coerceAtMost(24) else 12, fraction = job.fraction, height = 3.dp)
        }
    }
}

/** Error (Estados · No se pudo descargar): se queda hasta que se toca. «REINTENTAR» si se puede. */
@Composable
fun ErrorCard(error: ErrorNotice, onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .fpClickable(onClickLabel = "Cerrar aviso", pressedScale = 0.98f, onClick = onDismiss)
            .background(Color(0xF2161024))
            .leftBar(Fp.Neon, 2.dp)
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Ghost(Mood.Triste, 44.dp, Modifier.fpBob())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Nunito(error.title, 15.sp, weight = FontWeight.ExtraBold)
                Nunito(error.body, 13.sp, color = Fp.Mist, lineHeight = 18.sp)
            }
            if (error.retry != null) {
                PrimaryButton("REINTENTAR", onRetry, height = 36.dp, cutSize = 9.dp, fontSize = 11.sp, spacing = 1.5.sp, horizontalPadding = 14.dp)
            }
        }
    }
}

/** «Sin conexión»: arriba del todo hasta que vuelve la red. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xEB161024))
            .leftBar(Fp.Bone, 2.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ghost(Mood.Triste, 30.dp, Modifier.fpBob())
        Column(Modifier.weight(1f)) {
            Nunito("Sin conexión", 14.sp, weight = FontWeight.ExtraBold)
            Nunito("Tu biblioteca sigue aquí. Buscar y descargar vuelve cuando haya internet.", 12.sp, color = Fp.Mist, lineHeight = 16.sp)
        }
    }
}

/** El hueco del aviso «Sin conexión»: se abre y se cierra deslizando. */
@Composable
fun OfflineSlot(state: AppState, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible = !state.online,
        modifier = modifier,
        enter = if (reduce) fadeIn(tween(Motion.QUICK)) else expandVertically(tween(Motion.SLOW, easing = Motion.Standard)) + fadeIn(tween(Motion.SLOW)),
        exit = if (reduce) fadeOut(tween(Motion.QUICK)) else shrinkVertically(tween(Motion.STANDARD, easing = Motion.Standard)) + fadeOut(tween(Motion.QUICK)),
    ) {
        OfflineBanner()
    }
}

/**
 * Los avisos de abajo, apilados: error (fijo), aviso positivo (se va solo) y descarga en curso.
 * Cada uno entra subiendo y sale bajando.
 */
@Composable
fun NoticeStack(state: AppState, modifier: Modifier = Modifier) {
    val enter = riseIn()
    val exit = sinkOut()
    Column(modifier) {
        AnimatedContent(
            targetState = state.error,
            transitionSpec = { (enter togetherWith exit) using SizeTransform(clip = false) },
            contentKey = { it?.id },
            label = "error",
        ) { e ->
            if (e != null) ErrorCard(e, onRetry = { state.retryError() }, onDismiss = { state.dismissError() }, modifier = Modifier.padding(top = 8.dp))
            else Spacer(Modifier.fillMaxWidth())
        }
        AnimatedContent(
            targetState = state.toast,
            transitionSpec = { (enter togetherWith exit) using SizeTransform(clip = false) },
            contentKey = { it?.id },
            label = "toast",
        ) { t ->
            if (t != null) ToastCard(t, onAction = { state.runToastAction() }, modifier = Modifier.padding(top = 8.dp))
            else Spacer(Modifier.fillMaxWidth())
        }
        AnimatedContent(
            targetState = state.downloads.firstOrNull(),
            transitionSpec = { (enter togetherWith exit) using SizeTransform(clip = false) },
            contentKey = { it?.id },
            label = "download",
        ) { job ->
            if (job != null) DownloadCard(job, state.online, modifier = Modifier.padding(top = 8.dp))
            else Spacer(Modifier.fillMaxWidth())
        }
    }
}

/**
 * Estado que llena la pantalla (Biblioteca vacía, Nada por aquí, Sin conexión…):
 * recuadro discontinuo, fantasmas, título, texto y botones para salir de ahí.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    ghosts: @Composable () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize().heightIn(min = 220.dp).dashedBorder().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ghosts()
            Nunito(title, 15.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center)
            Nunito(body, 13.sp, Modifier.widthIn(max = 260.dp), color = Fp.Mist, lineHeight = 18.sp, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
        }
    }
}

/** Tarjeta de estado dentro de una pantalla (como las del catálogo de Estados). */
@Composable
fun StateCard(mood: Mood, title: String, body: String, edge: Color, modifier: Modifier = Modifier, bob: Boolean = false) {
    Row(
        modifier.fillMaxWidth().background(Color(0xD9161024)).leftBar(edge, 2.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Ghost(mood, 52.dp, if (bob) Modifier.fpBob() else Modifier.fpFloat())
        Column(Modifier.weight(1f)) {
            Nunito(title, 15.sp, weight = FontWeight.ExtraBold)
            Nunito(body, 13.sp, color = Fp.Mist, lineHeight = 18.sp)
        }
    }
}
