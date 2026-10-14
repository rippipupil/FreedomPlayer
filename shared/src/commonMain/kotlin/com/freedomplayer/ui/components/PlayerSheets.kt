package com.freedomplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.freedomplayer.core.songCount
import com.freedomplayer.core.timerLeftText
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.theme.Fp

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
