package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.LibraryStats
import com.freedomplayer.core.Song
import com.freedomplayer.core.Stats
import com.freedomplayer.core.timesText
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.Cover
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.FpTabs
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.Position
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.ScreenHeader
import com.freedomplayer.ui.components.SectionHeader
import com.freedomplayer.ui.components.SegmentBar
import com.freedomplayer.ui.components.SubText
import com.freedomplayer.ui.components.cut
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.nowMillis
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/** Lo que enseña Estadísticas para un periodo: el resumen y cuántas veces sonó cada canción. */
private class StatsView(val stats: LibraryStats, val plays: Map<String, Int>)

/**
 * Estadísticas (sencilla, tipo Wrapped): escuchas, minutos, canciones y con nota, los artistas
 * más escuchados y las canciones que más han sonado, en los últimos 30 días o desde siempre.
 */
@Composable
fun StatsScreen(state: AppState, modifier: Modifier = Modifier) {
    val pad = screenPadding()
    val reduce = LocalReduceMotion.current
    Column(modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        val sides = Modifier.padding(
            start = pad.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = pad.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        )
        Column(sides, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenHeader(if (state.statsAllTime) "TU MÚSICA · SIEMPRE" else "TU MÚSICA · 30 DÍAS", "Estadísticas")
            FpTabs(
                labels = listOf("30 DÍAS", "SIEMPRE"),
                selected = if (state.statsAllTime) 1 else 0,
                onSelect = { state.statsAllTime = it == 1 },
            )
        }
        AnimatedContent(
            targetState = state.statsAllTime,
            transitionSpec = {
                fadeIn(tween(if (reduce) Motion.QUICK else Motion.STANDARD)) togetherWith fadeOut(tween(Motion.QUICK))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "statsPeriod",
        ) { allTime ->
            StatsBody(state, rememberStatsView(state, allTime), pad.calculateBottomPadding(), sides)
        }
    }
}

/** Los números de un periodo; se recalculan solos al escuchar o puntuar. */
@Composable
private fun rememberStatsView(state: AppState, allTime: Boolean): StatsView {
    val view by remember(state, allTime) {
        derivedStateOf {
            val library = state.library
            if (allTime) {
                StatsView(Stats.of(library), library.associate { it.id to it.plays })
            } else {
                val since = nowMillis() - 30 * Stats.DAY_MS
                StatsView(Stats.period(library, state.playLog, since), Stats.playsSince(state.playLog, since))
            }
        }
    }
    return view
}

@Composable
private fun StatsBody(state: AppState, view: StatsView, bottom: androidx.compose.ui.unit.Dp, sides: Modifier) {
    val s = view.stats
    if (s.totalPlays == 0) {
        Box(sides.padding(top = 16.dp, bottom = bottom)) {
            EmptyState(
                title = if (state.library.isEmpty()) "Biblioteca vacía" else "Nada en 30 días",
                body = if (state.library.isEmpty()) "Cuando escuches música, aquí verás tus números." else "No has escuchado nada este mes. Mira lo de siempre.",
                ghosts = { Ghost(Mood.Dormido, 52.dp, Modifier.fpFloat()) },
            ) {
                if (state.library.isNotEmpty()) {
                    PrimaryButton("VER SIEMPRE", { state.statsAllTime = true }, Modifier.width(200.dp), fill = true)
                }
            }
        }
        return
    }
    val wide = LocalWide.current
    val maxArtist = s.topArtists.maxOfOrNull { it.second } ?: 1
    val songIds = s.topSongs.map { it.id }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "numbers") {
            BoxWithConstraints(sides.fillMaxWidth()) {
                val columns = if (maxWidth >= 560.dp) 4 else 2
                val cards = listOf(
                    "ESCUCHAS" to s.totalPlays,
                    "MINUTOS" to s.minutes,
                    "CANCIONES" to s.songCount,
                    "CON NOTA" to s.ratedCount,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cards.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (label, value) ->
                                NumberCard(label, value, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item(key = "artistsHeader") {
            SectionHeader("01", "ARTISTAS", sides.padding(top = 12.dp, bottom = 2.dp))
        }
        itemsIndexed(s.topArtists, key = { _, a -> "artist-" + a.first }) { i, (artist, plays) ->
            ArtistRow(i + 1, artist, plays, plays / maxArtist.toFloat(), wide, sides.then(itemMotion()))
        }
        item(key = "songsHeader") {
            SectionHeader("02", "CANCIONES", sides.padding(top = 12.dp, bottom = 2.dp))
        }
        itemsIndexed(s.topSongs, key = { _, song -> "song-" + song.id }) { i, song ->
            TopSongRow(i + 1, song, view.plays[song.id] ?: 0, sides.then(itemMotion())) {
                state.playFrom(songIds, song.id, "ESTADÍSTICAS")
            }
        }
    }
}

/** Tarjeta de un número grande (sube contando al cambiar de periodo). */
@Composable
private fun NumberCard(label: String, value: Int, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(value, tween(if (LocalReduceMotion.current) 0 else Motion.SLOW, easing = Motion.Standard), label = "number")
    val shape = cut(12.dp)
    Column(
        modifier
            .background(Fp.Card, shape)
            .border(1.dp, Fp.Pink.copy(alpha = 0.25f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Chakra(shown.toString(), 28.sp, color = Fp.Neon, weight = FontWeight.Bold)
        Chakra(label, 11.sp, color = Fp.Mist, spacing = 2.sp)
    }
}

/** Artista: puesto, nombre, escuchas y una barra de segmentos proporcional. El n.º 1, con el OwO. */
@Composable
private fun ArtistRow(position: Int, artist: String, plays: Int, fraction: Float, wide: Boolean, modifier: Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Position(position, Fp.MostPlayedPos.getOrElse(position - 1) { Fp.RankPos[3] }, 20.sp, 24.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Nunito(artist, 14.sp, Modifier.weight(1f, fill = false), weight = FontWeight.ExtraBold, maxLines = 1)
                if (position == 1) Ghost(Mood.Owo, 22.dp, Modifier.offset(y = (-2).dp).fpFloat())
            }
            SegmentBar(count = if (wide) 40 else 24, fraction = fraction, height = 4.dp, color = if (position == 1) Fp.Neon else Fp.Pink)
        }
        Chakra(timesText(plays), 12.sp, color = Fp.Mist, weight = FontWeight.Medium)
    }
}

/** Canción más escuchada del periodo: tocarla la pone a sonar. */
@Composable
private fun TopSongRow(position: Int, song: Song, plays: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Position(position, Fp.Neon, 16.sp, 24.dp)
        Cover(song.coverColor, 40.dp)
        Column(Modifier.weight(1f)) {
            Nunito(song.title, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            SubText(song.artist)
        }
        Chakra(timesText(plays), 12.sp, color = Fp.Mist, weight = FontWeight.Medium)
    }
}
