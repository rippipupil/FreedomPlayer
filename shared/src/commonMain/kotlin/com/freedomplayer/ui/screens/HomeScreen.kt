package com.freedomplayer.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.Ranking
import com.freedomplayer.core.Song
import com.freedomplayer.core.Stats
import com.freedomplayer.core.greetingFor
import com.freedomplayer.core.timesText
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.Cover
import com.freedomplayer.ui.components.CoverImage
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.Position
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.ScreenHeader
import com.freedomplayer.ui.components.SecondaryButton
import com.freedomplayer.ui.components.SectionHeader
import com.freedomplayer.ui.components.StarsView
import com.freedomplayer.ui.components.SubText
import com.freedomplayer.ui.components.SyncBadge
import com.freedomplayer.ui.components.TextAction
import com.freedomplayer.ui.components.cut
import com.freedomplayer.ui.components.fpBob
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.currentHour
import com.freedomplayer.ui.nowMillis
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide
import kotlinx.coroutines.delay

/** Lo que enseña Inicio, calculado de la biblioteca y del historial. */
private class HomeData(
    val recent: List<Song>,
    val most: List<Pair<Song, Int>>,
    val top: List<Song>,
)

@Composable
private fun rememberHomeData(state: AppState, recentCount: Int): HomeData {
    val data by remember(state, recentCount) {
        derivedStateOf {
            val library = state.library
            HomeData(
                // Recientes = lo último que ha sonado (historial guardado).
                recent = state.recentSongs(recentCount),
                // Más escuchadas = las de los últimos 30 días.
                most = state.mostPlayed(days = 30, top = 3),
                top = Ranking.general(library).take(3),
            )
        }
    }
    return data
}

/** «BUENOS DÍAS», «BUENAS TARDES» o «BUENAS NOCHES»; se actualiza solo. */
@Composable
private fun greeting(): String {
    val hour by produceState(currentHour()) {
        while (true) {
            delay(60_000)
            value = currentHour()
        }
    }
    return greetingFor(hour)
}

/** Inicio: Recientes, Más escuchadas (30 días) y Tu ranking. */
@Composable
fun HomeScreen(state: AppState, modifier: Modifier = Modifier) {
    if (LocalWide.current) HomeWide(state, modifier) else HomeMobile(state, modifier)
}

@Composable
private fun HomeHeader(state: AppState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (wide) Alignment.Bottom else Alignment.Top,
    ) {
        ScreenHeader(greeting(), "Inicio", sparkle = !wide)
        SyncBadge(
            if (wide) "SYNC · MÓVIL" else "SYNC · PC",
            onClick = { state.soon("Sincronizar con " + if (wide) "el móvil" else "el PC") },
            // En el PC va abajo del todo de la cabecera (en el diseño, la caja del título es más alta).
            modifier = if (wide) Modifier.offset(y = 12.dp) else Modifier.padding(top = 6.dp),
        )
    }
}

/** Biblioteca vacía: llena la pantalla y lleva a Buscar o a vincular el PC. */
@Composable
internal fun EmptyLibrary(state: AppState, modifier: Modifier = Modifier) {
    EmptyState(
        title = "Biblioteca vacía",
        body = "Busca tu primera canción o vincula el PC con un QR.",
        modifier = modifier,
        ghosts = {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ghost(Mood.Feliz, 52.dp, Modifier.fpFloat())
                Ghost(Mood.Dormido, 44.dp, Modifier.fpBob())
            }
        },
    ) {
        PrimaryButton("BUSCAR", { state.go(Screen.Search) }, Modifier.width(200.dp), fill = true)
        SecondaryButton("VINCULAR PC", { state.soon("Vincular con un QR") }, Modifier.width(200.dp))
    }
}

// ---------- Móvil ----------

@Composable
private fun HomeMobile(state: AppState, modifier: Modifier) {
    val data = rememberHomeData(state, recentCount = 3)
    val empty = state.library.isEmpty()
    val pad = screenPadding(mobile = 30.dp)
    if (empty) {
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            HomeHeader(state)
            EmptyLibrary(state, Modifier.weight(1f))
        }
        return
    }
    val mostIds = data.most.map { it.first.id }
    LazyColumn(modifier.fillMaxSize(), contentPadding = pad) {
        item(key = "header") {
            HomeHeader(state, Modifier.padding(bottom = 22.dp))
        }
        item(key = "recent") {
            Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader("01", "RECIENTES")
                RecentRow(state, data.recent, cover = 98.dp, gap = 14.dp)
            }
        }
        item(key = "mostHeader") {
            SectionHeader("02", "MÁS ESCUCHADAS", Modifier.padding(bottom = 9.dp))
        }
        if (data.most.isEmpty()) {
            item(key = "mostEmpty") {
                HintNote("Aquí salen las que más escuchas en los últimos 30 días.", Modifier.padding(bottom = 8.dp))
            }
        }
        itemsIndexed(data.most, key = { _, p -> "most-" + p.first.id }) { i, (song, count) ->
            MostPlayedRow(
                position = i + 1,
                song = song,
                count = count,
                onClick = { state.playFrom(mostIds, song.id, "MÁS ESCUCHADAS") },
                modifier = itemMotion().padding(bottom = 8.dp),
            )
        }
        item(key = "rank") {
            RankingCard(state, data.top, Modifier.padding(top = 12.dp))
        }
    }
}

// ---------- PC ----------

@Composable
private fun HomeWide(state: AppState, modifier: Modifier) {
    val data = rememberHomeData(state, recentCount = 6)
    val pad = screenPadding()
    if (state.library.isEmpty()) {
        Column(modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            HomeHeader(state)
            EmptyLibrary(state, Modifier.weight(1f))
        }
        return
    }
    val mostIds = data.most.map { it.first.id }
    val topIds = data.top.map { it.id }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        HomeHeader(state)
        Column(Modifier.padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader("01", "RECIENTES")
            RecentRow(state, data.recent, cover = 140.dp, gap = 26.dp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 640.dp
            val most: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader("02", "MÁS ESCUCHADAS", Modifier.padding(bottom = 4.dp))
                    if (data.most.isEmpty()) HintNote("Aquí salen las que más escuchas en los últimos 30 días.")
                    data.most.forEachIndexed { i, (song, count) ->
                        androidx.compose.runtime.key(song.id) {
                            MostPlayedRow(
                                position = i + 1,
                                song = song,
                                count = count,
                                onClick = { state.playFrom(mostIds, song.id, "MÁS ESCUCHADAS") },
                            )
                        }
                    }
                }
            }
            val rank: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("03", "TU RANKING", Modifier.padding(bottom = 2.dp))
                    data.top.forEachIndexed { i, song ->
                        androidx.compose.runtime.key(song.id) {
                            RankingLine(i + 1, song, wide = true) { state.playFrom(topIds, song.id, "TU RANKING") }
                        }
                    }
                }
            }
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    most(Modifier.weight(1f))
                    rank(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    most(Modifier.fillMaxWidth())
                    rank(Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- Piezas ----------

/** Inclinación y caída de cada foto clavada (las del diseño). */
private val Tilts = listOf(-3f to 0, 2f to 6, -1.5f to 0, 2.5f to 4, -2f to 6, 1f to 0)

/**
 * Las carátulas de Recientes, como fotos clavadas en un corcho. Tocar una la pone a sonar.
 * En el móvil encogen un poco si la pantalla es estrecha; en el PC, si no caben, se desplazan.
 */
@Composable
private fun RecentRow(state: AppState, recent: List<Song>, cover: Dp, gap: Dp) {
    if (recent.isEmpty()) {
        HintNote("Aún no ha sonado nada. Toca una canción en el panel de la izquierda y aparecerá aquí.")
        return
    }
    val ids = recent.map { it.id }
    val wide = LocalWide.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val slots = maxOf(recent.size, 1)
        val size = if (wide) cover else minOf(cover, (maxWidth - gap * (slots - 1)) / slots)
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(top = if (wide) 0.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            recent.forEachIndexed { i, song ->
                val (tilt, drop) = Tilts[i % Tilts.size]
                val dropDp = if (wide && i == 1) 8.dp else drop.dp
                // Si cambia la canción de un hueco (acaba de sonar otra), se funde con la nueva.
                Crossfade(targetState = song, animationSpec = tween(Motion.SLOW), label = "recent$i") { s ->
                    PinnedCard(s, size, tilt, dropDp) { state.playFrom(ids, s.id, "RECIENTES") }
                }
            }
        }
    }
}

@Composable
private fun PinnedCard(song: Song, size: Dp, tilt: Float, drop: Dp, onClick: () -> Unit) {
    val wide = LocalWide.current
    val shape = RoundedCornerShape(3.dp)
    Column(
        Modifier
            .width(size)
            .graphicsLayer {
                rotationZ = tilt
                translationY = drop.toPx()
            }
            .fpClickable(onClickLabel = "Reproducir", pressedScale = 0.96f, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp),
    ) {
        Box(Modifier.size(size)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(if (wide) 16.dp else 12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape)
                    .background(Color(song.coverColor)),
            ) {
                CoverImage(song.cover, Modifier.fillMaxSize())
            }
            // La chincheta.
            val pin = if (wide) 12.dp else 10.dp
            Box(
                Modifier
                    .offset(x = size * 0.45f, y = if (wide) (-5).dp else (-4).dp)
                    .size(pin)
                    .shadow(2.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .background(Fp.Bone, CircleShape),
            )
        }
        Column {
            Nunito(song.title, if (wide) 14.sp else 13.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            Nunito(
                song.artist,
                if (wide) 13.sp else 12.sp,
                Modifier.offset(y = if (wide) (-2).dp else 0.dp),
                color = Fp.Mist,
                maxLines = 1,
            )
        }
    }
}

/** Fila de Más escuchadas: puesto, carátula, título, artista y «84×». */
@Composable
private fun MostPlayedRow(position: Int, song: Song, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 12.dp),
    ) {
        Position(position, Fp.MostPlayedPos.getOrElse(position - 1) { Fp.MistDim }, 20.sp, if (wide) 24.dp else 22.dp)
        Cover(song.coverColor, if (wide) 40.dp else 42.dp, image = song.cover)
        Column(Modifier.weight(1f)) {
            Nunito(song.title, 14.sp, color = Fp.Bone, weight = FontWeight.ExtraBold, maxLines = 1)
            SubText(song.artist)
        }
        Chakra(timesText(count), 12.sp, color = Fp.Mist, weight = FontWeight.Medium)
    }
}

/** Fila de Tu ranking: puesto, título y la nota en ✦. */
@Composable
private fun RankingLine(position: Int, song: Song, wide: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (wide) Modifier.heightIn(min = 28.dp) else Modifier)
            .fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 10.dp),
    ) {
        Position(position, Fp.Neon, if (wide) 20.sp else 16.sp, if (wide) 24.dp else 16.dp)
        Nunito(
            song.title,
            14.sp,
            Modifier.weight(1f),
            weight = if (wide) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
        )
        StarsView(song.score, 14.dp)
    }
}

/** Tarjeta 03 TU RANKING del móvil: borde rosa, esquinas cortadas y «VER TODO →». */
@Composable
private fun RankingCard(state: AppState, top: List<Song>, modifier: Modifier = Modifier) {
    val shape = cut(14.dp)
    val ids = top.map { it.id }
    Column(
        modifier
            .fillMaxWidth()
            .background(Fp.Card, shape)
            .border(1.dp, Fp.Neon.copy(alpha = 0.5f), shape)
            .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 15.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionHeader("03", "TU RANKING", line = false) {
            TextAction("VER TODO →", { state.go(Screen.Rank) }, Modifier.offset(x = 6.dp))
        }
        if (top.isEmpty()) {
            Nunito("Puntúa canciones en Ranking y aquí verás tus favoritas.", 13.sp, color = Fp.Mist)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            top.forEachIndexed { i, song ->
                androidx.compose.runtime.key(song.id) {
                    RankingLine(i + 1, song, wide = false) { state.playFrom(ids, song.id, "TU RANKING") }
                }
            }
        }
    }
}
