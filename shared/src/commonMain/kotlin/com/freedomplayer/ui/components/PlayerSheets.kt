package com.freedomplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.freedomplayer.core.EqBands
import com.freedomplayer.core.EqPreset
import com.freedomplayer.core.songCount
import com.freedomplayer.core.timerLeftText
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.theme.Fp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Las claves de las filas de la cola: el id y, si una canción está dos veces, cuántas van antes
 * (así cada fila sigue siendo la misma al moverla).
 */
private fun queueKeys(items: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { id ->
        val n = seen[id] ?: 0
        seen[id] = n + 1
        "$id#$n"
    }
}

/**
 * La cola: lo que ya ha sonado (más apagado), la que suena y lo que viene. Se reordena
 * arrastrando ≡, se quita con × (la que suena no) y se toca una para que suene.
 */
@Composable
internal fun ColumnScope.QueueSheet(state: AppState) {
    val queue = state.queue
    val keys = queueKeys(queue.items)
    val upcoming = queue.upcoming.size
    SheetHeader("COLA · ${songCount(queue.items.size)}", state.queueSource.ifBlank { "Lo que suena y lo que viene" })
    if (queue.isEmpty) {
        SubText("No hay nada en la cola. Toca una canción de tu biblioteca para empezar.")
    } else {
        SubText(
            if (upcoming == 0) "Es la última. Arrastra ≡ para ordenar; toca una para que suene."
            else "Después suenan $upcoming más. Arrastra ≡ para ordenar; toca una para que suene.",
        )
        var draggingKey by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val heights = remember { HashMap<String, Int>() }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (queue.index - 1).coerceAtLeast(0))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), state = list) {
            itemsIndexed(queue.items, key = { i, _ -> keys[i] }) { i, id ->
                val song = state.song(id) ?: return@itemsIndexed
                val key = keys[i]
                val dragging = draggingKey == key
                val current = i == queue.index
                val played = i < queue.index
                val count = queue.items.size
                Box(
                    Modifier
                        .then(if (dragging) Modifier.zIndex(1f) else Modifier)
                        .then(if (dragging) Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null) else itemMotion())
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            val lift = if (dragging) 1.02f else 1f
                            scaleX = lift
                            scaleY = lift
                            alpha = if (played && !dragging) 0.55f else 1f
                        }
                        .background(if (dragging) Fp.Panel else Fp.Drawer)
                        .onSizeChanged { heights[key] = it.height }
                        .semantics(mergeDescendants = false) {
                            customActions = listOfNotNull(
                                if (i > 0) CustomAccessibilityAction("Subir ${song.title}") {
                                    state.moveInQueue(i, i - 1)
                                    true
                                } else null,
                                if (i < count - 1) CustomAccessibilityAction("Bajar ${song.title}") {
                                    state.moveInQueue(i, i + 1)
                                    true
                                } else null,
                                if (!current) CustomAccessibilityAction("Quitar ${song.title} de la cola") {
                                    state.removeFromQueue(i)
                                    true
                                } else null,
                            )
                        },
                ) {
                    SongRow(
                        song = song,
                        playing = current,
                        onClick = { state.playQueueItem(i) },
                        coverSize = 34.dp,
                        verticalPadding = 6.dp,
                        trailing = {
                            if (current) {
                                Chakra("SONANDO", 10.sp, color = Fp.Neon, spacing = 1.5.sp)
                            } else {
                                IconButton(FpIcons.Close, "Quitar ${song.title} de la cola", { state.removeFromQueue(i) }, size = 40.dp, iconSize = 14.dp, color = Fp.Mist)
                            }
                            DragHandle(
                                song.title,
                                dragging,
                                Modifier.pointerInput(key) {
                                    detectDragGestures(
                                        onDragStart = { _: Offset ->
                                            draggingKey = key
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingKey = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingKey = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val now = queueKeys(state.queue.items)
                                            val at = now.indexOf(key)
                                            val h = (heights[key] ?: 1).toFloat()
                                            if (dragOffset > h / 2f && at in 0 until now.lastIndex) {
                                                state.moveInQueue(at, at + 1)
                                                dragOffset -= h
                                            } else if (dragOffset < -h / 2f && at > 0) {
                                                state.moveInQueue(at, at - 1)
                                                dragOffset += h
                                            }
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }
    SecondaryButton("CERRAR", { state.sheet = null }, Modifier.fillMaxWidth(), color = Fp.Mist, lineColor = Fp.Pink.copy(alpha = 0.25f))
}

/** El asa ≡ para arrastrar una fila (44 dp de zona táctil). */
@Composable
private fun DragHandle(title: String, dragging: Boolean, modifier: Modifier) {
    val color by animateColorAsState(if (dragging) Fp.Neon else Fp.Idle, fpTween(Motion.QUICK), label = "queueHandle")
    Box(
        modifier
            .size(44.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = "Arrastrar para ordenar $title" },
        contentAlignment = Alignment.Center,
    ) {
        FpIconView(FpIcons.Handle, 18.dp, color)
    }
}

/** Minutos que se pueden elegir en el temporizador. */
private val SleepChoices = listOf(15, 30, 45, 60)

/** Temporizador de apagado: dentro de N minutos o al acabar la canción que suena. */
@Composable
internal fun ColumnScope.SleepTimerSheet(state: AppState) {
    val timer = state.sleepTimer
    val title = when {
        timer == null -> "Parar la música dentro de…"
        timer.endOfSong -> "Se para al acabar esta canción"
        else -> "Se para dentro de ${timerLeftText(timer.remainingMs)}"
    }
    SheetHeader("TEMPORIZADOR DE APAGADO", title)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (minutes in SleepChoices) {
            val chosen = timer != null && !timer.endOfSong && timer.minutes == minutes
            val bg by animateColorAsState(if (chosen) Fp.Neon else Fp.Panel, fpTween(), label = "sleepBg")
            val fg by animateColorAsState(if (chosen) Fp.Ink else Fp.Bone, fpTween(), label = "sleepFg")
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .fpClickable(onClickLabel = "Parar dentro de $minutes minutos") { state.startSleepTimer(minutes) }
                    .semantics { selected = chosen }
                    .background(bg, cut(10.dp))
                    .border(1.dp, Fp.Pink.copy(alpha = if (chosen) 0f else 0.35f), cut(10.dp))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Chakra(minutes.toString(), 20.sp, color = fg, weight = FontWeight.Bold, align = TextAlign.Center)
                Chakra("MIN", 10.sp, color = if (chosen) Fp.Ink else Fp.Mist, spacing = 1.5.sp, align = TextAlign.Center)
            }
        }
    }
    val song = state.currentSong
    OptionRow(
        FpIcons.Moon,
        "Al acabar esta canción",
        song?.let { "«${it.title}»" } ?: "Cuando suene algo.",
        enabled = song != null,
    ) { state.sleepAtEndOfSong() }
    if (timer != null) {
        OptionRow(FpIcons.Close, "Apagar el temporizador", "La música sigue sonando.") { state.cancelSleepTimer() }
    }
    SecondaryButton("CERRAR", { state.sheet = null }, Modifier.fillMaxWidth(), color = Fp.Mist, lineColor = Fp.Pink.copy(alpha = 0.25f))
}

/** «+3 dB», «0 dB», «−4,5 dB». */
private fun dbText(db: Float): String {
    val tenths = (db * 10).roundToInt()
    val whole = abs(tenths) / 10
    val dec = abs(tenths) % 10
    val sign = when {
        tenths > 0 -> "+"
        tenths < 0 -> "−"
        else -> ""
    }
    return sign + whole + (if (dec != 0) ",$dec" else "") + " dB"
}

/**
 * Ecualizador de 5 bandas: ecualizaciones hechas («Plano», «Más graves», «Voz»…) y cada banda
 * a mano (de −12 a +12 dB). Se oye al momento y se guarda.
 */
@Composable
internal fun ColumnScope.EqualizerSheet(state: AppState) {
    val gains = state.settings.eqGains
    val preset = EqPreset.matching(gains)
    SheetHeader("ECUALIZADOR", preset.label)
    if (!state.equalizerAvailable) {
        HintNote("Este móvil no deja usar su ecualizador. El volumen igualado y el fundido sí funcionan.")
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (p in EqPreset.choices) {
            ChoiceChip(p.label, p == preset) { state.settings.eqGains = p.gains }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 5) {
            BandSlider(EqBands.labels[i], gains.getOrElse(i) { 0f }) { v -> state.setEqBand(i, v) }
        }
    }
    SecondaryButton("CERRAR", { state.sheet = null }, Modifier.fillMaxWidth(), color = Fp.Mist, lineColor = Fp.Pink.copy(alpha = 0.25f))
}

@Composable
internal fun ChoiceChip(label: String, chosen: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (chosen) Fp.Neon else Fp.Panel, fpTween(), label = "presetBg")
    val fg by animateColorAsState(if (chosen) Fp.Ink else Fp.Bone, fpTween(), label = "presetFg")
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .fpClickable(onClickLabel = "Elegir $label", onClick = onClick)
            .semantics { selected = chosen },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .background(bg, cut(8.dp))
                .border(1.dp, Fp.Pink.copy(alpha = if (chosen) 0f else 0.35f), cut(8.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Nunito(label, 13.sp, color = fg, weight = FontWeight.Bold)
        }
    }
}

/**
 * Una banda: frecuencia, la barra (el 0 en medio, lo que sube o baja en Neón) con el rombo, y los
 * dB. Se toca o se arrastra; con accesibilidad, se ajusta como cualquier barra.
 */
@Composable
private fun BandSlider(label: String, db: Float, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val max = EqBands.MAX_DB
    val shown by animateFloatAsState(db, fpTween(Motion.QUICK), label = "band")
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Chakra(label, 11.sp, Modifier.width(64.dp), color = Fp.Mist, spacing = 1.sp)
        Canvas(
            Modifier
                .weight(1f)
                .height(44.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    fun valueAt(x: Float): Float {
                        val f = (x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        // De medio en medio dB, y el 0 «imanta» un poco.
                        val v = ((f * 2f - 1f) * max * 2f).roundToInt() / 2f
                        return if (abs(v) < 0.75f) 0f else v
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        change(valueAt(down.position.x))
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) {
                                c.consume()
                                break
                            }
                            change(valueAt(c.position.x))
                            c.consume()
                        }
                    }
                }
                .semantics {
                    contentDescription = "Banda de $label"
                    stateDescription = dbText(db)
                    progressBarRangeInfo = ProgressBarRangeInfo(db, -max..max, steps = 47)
                    setProgress { v ->
                        change((v * 2f).roundToInt() / 2f)
                        true
                    }
                },
        ) {
            val cy = this.size.height / 2f
            val w = this.size.width
            val line = 3.dp.toPx()
            drawRect(Fp.Track, topLeft = Offset(0f, cy - line / 2f), size = Size(w, line))
            val mid = w / 2f
            val x = mid + (shown / max) * mid
            drawRect(Fp.Neon, topLeft = Offset(minOf(mid, x), cy - line / 2f), size = Size(abs(x - mid), line))
            drawRect(Fp.Mist.copy(alpha = 0.6f), topLeft = Offset(mid - 1.dp.toPx() / 2f, cy - 6.dp.toPx()), size = Size(1.dp.toPx(), 12.dp.toPx()))
            val k = 7.dp.toPx()
            val knob = Path().apply {
                moveTo(x, cy - k)
                lineTo(x + k, cy)
                lineTo(x, cy + k)
                lineTo(x - k, cy)
                close()
            }
            drawPath(knob, Fp.Lamp)
        }
        Chakra(dbText(db), 12.sp, Modifier.width(60.dp), color = if (abs(db) < 0.05f) Fp.Mist else Fp.Neon, align = TextAlign.End)
    }
}
