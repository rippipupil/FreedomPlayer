package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.Ranking
import com.freedomplayer.core.Song
import com.freedomplayer.core.songCount
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Prompt
import com.freedomplayer.ui.RankTab
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.Cover
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.FpTabs
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.Position
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.ScreenHeader
import com.freedomplayer.ui.components.StarsInput
import com.freedomplayer.ui.components.StarsView
import com.freedomplayer.ui.components.SubText
import com.freedomplayer.ui.components.TextAction
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.highlight
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.components.starsLabel
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/**
 * Ranking: General (las puntuadas, de mayor a menor), Míos (rankings ordenados a mano) y
 * Sin nota (las escuchadas sin nota, para puntuarlas). Tocar las ✦ de una fila de General
 * permite cambiar su nota; después sale «DESHACER» unos segundos.
 */
@Composable
fun RankingScreen(state: AppState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val pad = screenPadding()
    val sides = PaddingValues(start = pad.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), end = pad.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr))
    val general by remember(state) { derivedStateOf { Ranking.general(state.library) } }
    val unrated by remember(state) { derivedStateOf { Ranking.unrated(state.library) } }
    Column(modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
        Column(Modifier.padding(sides), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                ScreenHeader("TUS NOTAS · 1–10", "Ranking", sparkle = true)
                RatedCounter(rated = general.size, total = state.library.size, modifier = Modifier.padding(bottom = 6.dp))
            }
            FpTabs(
                labels = listOf("GENERAL", "MÍOS", "SIN NOTA"),
                selected = state.rankTab.ordinal,
                onSelect = {
                    state.rankTab = RankTab.entries[it]
                    state.editingScoreId = null
                },
                badges = listOf(0, 0, unrated.size),
                tabPadding = 12.dp,
                underlineInset = 8.dp,
                spacing = 1.5.sp,
            )
        }
        val reduce = LocalReduceMotion.current
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.library.isEmpty()) {
                EmptyLibrary(state, Modifier.padding(sides).padding(top = 14.dp, bottom = 12.dp))
            } else AnimatedContent(
                targetState = state.rankTab,
                transitionSpec = {
                    if (reduce) {
                        fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                    } else {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(Motion.STANDARD)) + slideInHorizontally(tween(Motion.STANDARD, easing = Motion.Standard)) { dir * it / 10 }) togetherWith
                            (fadeOut(tween(Motion.QUICK)) + slideOutHorizontally(tween(Motion.QUICK)) { -dir * it / 10 })
                    }
                },
                label = "rankTab",
            ) { tab ->
                val listPad = PaddingValues(
                    start = sides.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = sides.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = 14.dp,
                    bottom = 16.dp,
                )
                when (tab) {
                    RankTab.General -> GeneralList(state, general, listPad)
                    RankTab.Mine -> MineList(state, listPad)
                    RankTab.Unrated -> UnratedList(state, unrated, listPad, wide)
                }
            }
        }
    }
}

/** «186 / 248»: el número sube o baja deslizando al puntuar. */
@Composable
private fun RatedCounter(rated: Int, total: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "$rated de $total puntuadas" }, verticalAlignment = Alignment.Bottom) {
        AnimatedContent(
            targetState = rated,
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                } else {
                    val up = targetState > initialState
                    (fadeIn(tween(Motion.STANDARD)) + slideInVertically(tween(Motion.STANDARD, easing = Motion.Standard)) { if (up) it / 2 else -it / 2 }) togetherWith
                        (fadeOut(tween(Motion.QUICK)) + slideOutVertically(tween(Motion.QUICK)) { if (up) -it / 2 else it / 2 })
                }
            },
            label = "ratedCount",
        ) { n ->
            Chakra(n.toString(), 16.sp, color = Fp.Neon, weight = FontWeight.Bold)
        }
        Chakra(" / $total", 11.sp, Modifier.padding(bottom = 2.dp), color = Fp.Mist, spacing = 1.5.sp)
    }
}

// ---------- General ----------

@Composable
private fun GeneralList(state: AppState, general: List<Song>, pad: PaddingValues) {
    if (general.isEmpty()) {
        Column(Modifier.padding(pad)) {
            EmptyState(
                title = "Sin notas todavía",
                body = "Puntúa lo que escuchas en «Sin nota» y aparecerá aquí, de mayor a menor.",
                ghosts = { Ghost(Mood.Owo, 52.dp, Modifier.fpFloat()) },
            ) {
                PrimaryButton("IR A SIN NOTA", { state.rankTab = RankTab.Unrated }, Modifier.width(220.dp), fill = true)
            }
        }
        return
    }
    val ids = general.map { it.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(general, key = { _, s -> s.id }) { i, song ->
            GeneralRow(
                state = state,
                index = i,
                song = song,
                fresh = song.id in state.freshIds,
                editing = state.editingScoreId == song.id,
                onPlay = { state.playFrom(ids, song.id, "RANKING") },
                modifier = itemMotion(),
            )
        }
        item(key = "ties") {
            Nunito(
                "Empates: gana la que más veces has escuchado. Toca las ✦ de una canción para cambiar su nota.",
                11.sp,
                Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp).then(itemMotion()),
                color = Fp.MistDim,
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * Fila del ranking general: puesto, carátula (el n.º 1 con el fantasma OwO), título, ✦ y artista,
 * y la nota. La recién puntuada va en jade. Tocar las ✦ abre el selector para cambiar la nota.
 */
@Composable
private fun GeneralRow(
    state: AppState,
    index: Int,
    song: Song,
    fresh: Boolean,
    editing: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = index == 0
    val edge by animateColorAsState(
        when {
            fresh -> Fp.Jade
            first -> Fp.Neon
            else -> Color.Transparent
        },
        fpTween(Motion.SLOW),
        label = "rankEdge",
    )
    val bg by animateColorAsState(
        when {
            fresh -> Fp.Jade.copy(alpha = 0.12f)
            first -> Fp.Neon.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        fpTween(Motion.SLOW),
        label = "rankBg",
    )
    val playing = state.currentSong?.id == song.id
    Column(modifier.fillMaxWidth().highlight(edge, bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f, onClick = onPlay)
                .padding(start = 6.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Position(
                index + 1,
                Fp.RankPos[minOf(index, 3)],
                if (index < 3) 20.sp else 15.sp,
                26.dp,
                align = TextAlign.Center,
            )
            Box {
                Cover(song.coverColor, 40.dp)
                if (first) {
                    Ghost(Mood.Owo, 24.dp, Modifier.offset(x = 18.dp, y = (-16).dp).fpFloat())
                }
            }
            Column(Modifier.weight(1f)) {
                Nunito(song.title, 14.sp, color = if (playing) Fp.NeonSoft else Fp.Bone, weight = FontWeight.ExtraBold, maxLines = 1)
                Row(
                    Modifier
                        .padding(top = 2.dp)
                        .fpClickable(onClickLabel = "Cambiar la nota", pressedScale = 0.96f) {
                            state.editingScoreId = if (editing) null else song.id
                        }
                        .semantics { contentDescription = "Nota: ${starsLabel(song.score)}. Cambiar la nota de ${song.title}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StarsView(song.score, 12.dp, gap = 1.dp)
                    SubText(song.artist)
                }
            }
            Chakra(song.score?.toString() ?: "–", 18.sp, color = Fp.Pink, weight = FontWeight.Bold)
        }
        AnimatedVisibility(
            visible = editing,
            enter = expandVertically(tween(Motion.STANDARD, easing = Motion.Standard)) + fadeIn(tween(Motion.STANDARD)),
            exit = shrinkVertically(tween(Motion.QUICK, easing = Motion.Standard)) + fadeOut(tween(Motion.QUICK)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 36.dp, end = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StarsInput(
                    score = song.score,
                    onRate = { state.rate(song.id, it) },
                    label = "Nueva nota para ${song.title}",
                    cell = 44.dp,
                    starSize = 24.dp,
                )
                Spacer(Modifier.weight(1f))
                TextAction("LISTO", { state.editingScoreId = null }, color = Fp.Mist, size = 11.sp, spacing = 1.5.sp)
            }
        }
    }
}

// ---------- Míos ----------

@Composable
private fun MineList(state: AppState, pad: PaddingValues) {
    val open = state.rankings.firstOrNull { it.id == state.openRankingId }
    val others = state.rankings.filter { it.id != open?.id }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { HashMap<String, Int>() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        if (open != null) {
            item(key = "head-" + open.id) {
                CardPiece(top = true, modifier = itemMotion()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Nunito(open.name, 16.sp, Modifier.weight(1f, fill = false), weight = FontWeight.ExtraBold, maxLines = 1)
                        Chakra(
                            if (open.songIds.size > 1) "ARRASTRA PARA ORDENAR" else "",
                            11.sp,
                            Modifier.padding(start = 10.dp),
                            color = Fp.Mist,
                            spacing = 1.5.sp,
                        )
                    }
                }
            }
            val songs = open.songIds.mapNotNull { id -> state.song(id) }
            if (songs.isEmpty()) {
                item(key = "empty-" + open.id) {
                    CardPiece(modifier = itemMotion()) {
                        Nunito(
                            "Este ranking está vacío. Añadir canciones a un ranking llega en una próxima versión.",
                            13.sp,
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = Fp.Mist,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
            itemsIndexed(songs, key = { _, s -> "row-" + open.id + "-" + s.id }) { i, song ->
                val dragging = draggingId == song.id
                CardPiece(
                    modifier = Modifier
                        .then(if (dragging) Modifier.zIndex(1f) else Modifier)
                        .then(dragMotion(dragging))
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            val lift = if (dragging) 1.02f else 1f
                            scaleX = lift
                            scaleY = lift
                        }
                        .onSizeChanged { heights[song.id] = it.height },
                ) {
                    MineRow(
                        state = state,
                        ranking = open,
                        index = i,
                        song = song,
                        dragging = dragging,
                        handle = Modifier.pointerInput(open.id, song.id) {
                            detectDragGestures(
                                onDragStart = { _: Offset ->
                                    draggingId = song.id
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val ids = state.rankings.firstOrNull { it.id == open.id }?.songIds.orEmpty()
                                    val at = ids.indexOf(song.id)
                                    val h = (heights[song.id] ?: 1).toFloat()
                                    if (dragOffset > h / 2f && at in 0 until ids.lastIndex) {
                                        state.moveInRanking(open.id, at, at + 1)
                                        dragOffset -= h
                                    } else if (dragOffset < -h / 2f && at > 0) {
                                        state.moveInRanking(open.id, at, at - 1)
                                        dragOffset += h
                                    }
                                },
                            )
                        },
                    )
                }
            }
            item(key = "foot-" + open.id) {
                CardPiece(bottom = true, modifier = itemMotion()) { Spacer(Modifier.height(8.dp)) }
            }
        }
        item(key = "others-gap") { Spacer(Modifier.height(12.dp)) }
        items(others, key = { "other-" + it.id }) { r ->
            OtherRanking(state, r, itemMotion())
        }
        item(key = "new") {
            PrimaryButton(
                "+ NUEVO RANKING",
                { state.prompt = Prompt.NewRanking },
                Modifier.fillMaxWidth().padding(top = 12.dp).then(itemMotion()),
                fill = true,
            )
        }
    }
}

/** Mientras se arrastra, la fila no anima su sitio (lo lleva el dedo); las demás sí. */
@Composable
private fun LazyItemScope.dragMotion(dragging: Boolean): Modifier =
    if (dragging) Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null) else itemMotion()

/**
 * Un trozo de la tarjeta de un ranking (fondo #181127 y borde rosa): arriba con la esquina
 * cortada, abajo con la otra, y en medio solo los lados. Así cada fila es un elemento de la
 * lista y puede moverse sola al reordenar.
 */
@Composable
private fun CardPiece(
    modifier: Modifier = Modifier,
    top: Boolean = false,
    bottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    val line = Fp.Neon.copy(alpha = 0.5f)
    Box(
        modifier
            .fillMaxWidth()
            .background(Fp.Card, CutCornerShape(topStart = if (top) 14.dp else 0.dp, bottomEnd = if (bottom) 14.dp else 0.dp))
            .drawBehind {
                val px = 1.dp.toPx()
                val c = 14.dp.toPx()
                val w = this.size.width
                val h = this.size.height
                val path = Path().apply {
                    if (top) {
                        moveTo(px / 2, h)
                        lineTo(px / 2, c)
                        lineTo(c, px / 2)
                        lineTo(w - px / 2, px / 2)
                        lineTo(w - px / 2, if (bottom) h - c else h)
                        if (bottom) {
                            lineTo(w - c, h - px / 2)
                            lineTo(px / 2, h - px / 2)
                            close()
                        }
                    } else if (bottom) {
                        moveTo(px / 2, 0f)
                        lineTo(px / 2, h - px / 2)
                        lineTo(w - c, h - px / 2)
                        lineTo(w - px / 2, h - c)
                        lineTo(w - px / 2, 0f)
                    } else {
                        moveTo(px / 2, 0f)
                        lineTo(px / 2, h)
                        moveTo(w - px / 2, 0f)
                        lineTo(w - px / 2, h)
                    }
                }
                drawPath(path, line, style = Stroke(width = px))
            },
    ) {
        content()
    }
}

/** Fila de un ranking propio: puesto, carátula, título y el asa ≡ (44 dp) para arrastrar. */
@Composable
private fun MineRow(
    state: AppState,
    ranking: CustomRanking,
    index: Int,
    song: Song,
    dragging: Boolean,
    handle: Modifier,
) {
    val count = ranking.songIds.size
    val handleColor by animateColorAsState(if (dragging) Fp.Neon else Fp.Idle, fpTween(Motion.QUICK), label = "handle")
    val shade by animateFloatAsState(if (dragging) 1f else 0f, fpTween(Motion.QUICK), label = "dragShade")
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (shade > 0f) drawRect(Fp.Panel.copy(alpha = shade))
                drawRect(Fp.Pink.copy(alpha = 0.1f), topLeft = Offset(14.dp.toPx(), 0f), size = Size(this.size.width - 28.dp.toPx(), 1.dp.toPx()))
            }
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
            .semantics(mergeDescendants = true) {
                customActions = listOfNotNull(
                    if (index > 0) CustomAccessibilityAction("Subir ${song.title}") {
                        state.moveInRanking(ranking.id, index, index - 1)
                        true
                    } else null,
                    if (index < count - 1) CustomAccessibilityAction("Bajar ${song.title}") {
                        state.moveInRanking(ranking.id, index, index + 1)
                        true
                    } else null,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Position(index + 1, Fp.Neon, 16.sp, 20.dp)
        Cover(song.coverColor, 34.dp)
        Nunito(
            song.title,
            14.sp,
            Modifier.weight(1f).fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f) {
                state.playFrom(ranking.songIds, song.id, ranking.name.uppercase())
            },
            weight = FontWeight.Bold,
            maxLines = 1,
        )
        Box(
            handle
                .size(44.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = "Arrastrar para ordenar ${song.title}" },
            contentAlignment = Alignment.Center,
        ) {
            FpIconView(FpIcons.Handle, 16.dp, handleColor)
        }
    }
}

/** Otro ranking, plegado: carátula, nombre y «10 CANCIONES». Tocarlo lo abre arriba. */
@Composable
private fun OtherRanking(state: AppState, ranking: CustomRanking, modifier: Modifier = Modifier) {
    val cover = state.song(ranking.songIds.firstOrNull())?.coverColor ?: 0xFF241A38
    Row(
        modifier
            .fillMaxWidth()
            .fpClickable(onClickLabel = "Abrir ranking", pressedScale = 0.98f) { state.openRankingId = ranking.id }
            .drawBehind {
                drawRect(Fp.Pink.copy(alpha = 0.12f), topLeft = Offset(0f, this.size.height - 1.dp.toPx()), size = Size(this.size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(cover, 34.dp)
        Column(Modifier.weight(1f)) {
            Nunito(ranking.name, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            Chakra(songCount(ranking.songIds.size), 11.sp, color = Fp.Mist, spacing = 1.sp)
        }
    }
}

// ---------- Sin nota ----------

@Composable
private fun UnratedList(state: AppState, unrated: List<Song>, pad: PaddingValues, wide: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "hint") {
            HintNote("Toca la mitad izquierda o derecha de cada ✦ para poner medias.")
        }
        if (unrated.isEmpty()) {
            item(key = "done") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 30.dp).then(itemMotion()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Ghost(Mood.Uwu, 64.dp, Modifier.fpFloat())
                    Nunito("¡Todo puntuado!", 15.sp, weight = FontWeight.ExtraBold)
                }
            }
        }
        items(unrated, key = { it.id }) { song ->
            UnratedRow(state, song, wide, itemMotion())
        }
    }
}

/**
 * Canción escuchada sin nota: carátula, título, «Artista · 12 escuchas» y las cinco ✦ para puntuar
 * (casillas de 44 dp). En el móvil las ✦ empiezan 12 dp más adentro para no tocar la pestaña izquierda.
 */
@Composable
private fun UnratedRow(state: AppState, song: Song, wide: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Fp.Pink.copy(alpha = 0.12f), topLeft = Offset(0f, this.size.height - 1.dp.toPx()), size = Size(this.size.width, 1.dp.toPx()))
            }
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f) {
                state.playFrom(listOf(song.id), song.id, "SIN NOTA")
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Cover(song.coverColor, 40.dp)
            Column(Modifier.weight(1f)) {
                Nunito(song.title, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                SubText("${song.artist} · ${song.plays} ${if (song.plays == 1) "escucha" else "escuchas"}")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = if (wide) 0.dp else starsInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StarsInput(
                score = null,
                onRate = { state.rate(song.id, it) },
                label = "Puntuar ${song.title}",
            )
            Spacer(Modifier.weight(1f))
            Chakra("SIN NOTA", 11.sp, color = Fp.MistDim, spacing = 1.5.sp)
        }
    }
}

/** Lo que se meten las ✦ de Sin nota en el móvil para no quedar bajo la pestaña izquierda (44 dp). */
private val starsInset: Dp = 12.dp
