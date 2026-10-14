package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.Highlight
import com.freedomplayer.core.LibraryStats
import com.freedomplayer.core.Song
import com.freedomplayer.core.Stats
import com.freedomplayer.core.YearMonth
import com.freedomplayer.core.timesText
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.StatsTab
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.Cover
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.FpTabs
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.IconButton
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
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.nowMillis
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide
import com.freedomplayer.ui.utcOffsetMillis

/**
 * Lo que enseña Estadísticas para un periodo: el resumen, cuántas veces sonó cada canción, lo
 * más destacado del ranking y cuántas sonaron por primera vez (solo por meses).
 */
private class StatsView(
    val key: String,
    val stats: LibraryStats,
    val plays: Map<String, Int>,
    val highlights: List<Highlight>,
    val discovered: Int?,
)

private val offsetAt: (Long) -> Long = { utcOffsetMillis(it) }

/**
 * Estadísticas tipo Wrapped, con el historial de escuchas de verdad: escuchas, minutos, canciones,
 * los artistas y las canciones que más han sonado y lo más destacado de tu ranking, de los
 * últimos 30 días, de cada mes (con la barra de los últimos 12) o desde siempre.
 */
@Composable
fun StatsScreen(state: AppState, modifier: Modifier = Modifier) {
    val pad = screenPadding()
    val reduce = LocalReduceMotion.current
    val sides = Modifier.padding(start = pad.calculateLeftPadding(LayoutDirection.Ltr), end = pad.calculateRightPadding(LayoutDirection.Ltr))
    val months by remember(state) { derivedStateOf { Stats.months(state.playLog, offsetAt) } }
    val month = state.statsMonth?.takeIf { it in months } ?: months.firstOrNull() ?: YearMonth.of(nowMillis(), utcOffsetMillis(nowMillis()))
    Column(modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        Column(sides, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val label = when (state.statsTab) {
                StatsTab.Recent -> "TU MÚSICA · 30 DÍAS"
                StatsTab.Monthly -> "TU MÚSICA · POR MESES"
                StatsTab.AllTime -> "TU MÚSICA · SIEMPRE"
            }
            ScreenHeader(label, "Estadísticas")
            FpTabs(
                labels = listOf("30 DÍAS", "POR MES", "SIEMPRE"),
                selected = state.statsTab.ordinal,
                onSelect = { state.statsTab = StatsTab.entries[it] },
            )
        }
        AnimatedContent(
            targetState = state.statsTab,
            transitionSpec = {
                fadeIn(tween(if (reduce) Motion.QUICK else Motion.STANDARD)) togetherWith fadeOut(tween(Motion.QUICK))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "statsTab",
        ) { tab ->
            if (tab == StatsTab.Monthly) {
                MonthlyStats(state, months, month, pad.calculateBottomPadding(), sides)
            } else {
                StatsBody(state, rememberStatsView(state, tab, month), pad.calculateBottomPadding(), sides, header = null)
            }
        }
    }
}

/** Los números de un periodo; se recalculan solos al escuchar o puntuar. */
@Composable
private fun rememberStatsView(state: AppState, tab: StatsTab, month: YearMonth): StatsView {
    val view by remember(state, tab, month) {
        derivedStateOf {
            val library = state.library
            val log = state.playLog
            when (tab) {
                StatsTab.Recent -> {
                    val counts = Stats.playsSince(log, nowMillis() - 30 * Stats.DAY_MS)
                    StatsView("recent", Stats.summary(library, counts), counts, Stats.highlights(library, counts), null)
                }
                StatsTab.AllTime -> {
                    // El historial de verdad; si aún no hay (bibliotecas de antes), los contadores.
                    val counts = if (log.isEmpty()) library.filter { it.plays > 0 }.associate { it.id to it.plays } else log.groupingBy { it.songId }.eachCount()
                    StatsView("all", Stats.summary(library, counts), counts, Stats.highlights(library, counts), null)
                }
                StatsTab.Monthly -> {
                    val counts = Stats.playsIn(log, month, offsetAt)
                    val found = Stats.discoveredIn(log, month, offsetAt)
                    StatsView("m-${month.year}-${month.month}", Stats.summary(library, counts), counts, Stats.highlights(library, counts, found), found.count { it in counts })
                }
            }
        }
    }
    return view
}

/** Por meses: el mes elegido (‹ ›), las escuchas de los últimos 12 meses y el resumen del mes. */
@Composable
private fun MonthlyStats(state: AppState, months: List<YearMonth>, month: YearMonth, bottom: Dp, sides: Modifier) {
    val reduce = LocalReduceMotion.current
    val i = months.indexOf(month)
    val older = months.getOrNull(i + 1)
    val newer = if (i > 0) months[i - 1] else null
    Column(Modifier.fillMaxSize()) {
        Row(sides.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(FpIcons.Back, "Mes anterior", { state.statsMonth = older }, enabled = older != null, color = Fp.Pink)
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val forward = targetState > initialState
                    if (reduce) {
                        fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                    } else {
                        (fadeIn(tween(Motion.STANDARD)) + slideInHorizontally(tween(Motion.STANDARD, easing = Motion.Standard)) { if (forward) it / 3 else -it / 3 }) togetherWith
                            (fadeOut(tween(Motion.QUICK)) + slideOutHorizontally(tween(Motion.STANDARD, easing = Motion.Standard)) { if (forward) -it / 3 else it / 3 })
                    }
                },
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
                label = "statsMonth",
            ) { m ->
                Chakra(m.label, 16.sp, Modifier.fillMaxWidth().semantics { heading() }, color = Fp.Bone, weight = FontWeight.Bold, spacing = 2.sp, align = TextAlign.Center)
            }
            IconButton(FpIcons.Back, "Mes siguiente", { state.statsMonth = newer }, Modifier.rotate(180f), enabled = newer != null, color = Fp.Pink)
        }
        val last = months.firstOrNull() ?: month
        val bars by remember(state, last) { derivedStateOf { Stats.playsPerMonth(state.playLog, last, 12, offsetAt) } }
        MonthBars(bars, month, sides.padding(top = 6.dp, bottom = 4.dp)) { m ->
            if (m in months) {
                state.statsMonth = m
            }
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = { fadeIn(tween(if (reduce) Motion.QUICK else Motion.STANDARD)) togetherWith fadeOut(tween(Motion.QUICK)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "statsMonthBody",
        ) { m ->
            StatsBody(state, rememberStatsView(state, StatsTab.Monthly, m), bottom, sides, header = m)
        }
    }
}

/** Las escuchas de los últimos 12 meses: el elegido en Neón; tocar una barra lleva a ese mes. Crecen al aparecer. */
@Composable
private fun MonthBars(bars: List<Pair<YearMonth, Int>>, selected: YearMonth, modifier: Modifier, onPick: (YearMonth) -> Unit) {
    val reduce = LocalReduceMotion.current
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) grow.animateTo(1f, tween(Motion.SLOW * 2, easing = Motion.Standard)) }
    val max = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for ((m, plays) in bars) {
            val on = m == selected
            val height by animateFloatAsState(plays / max.toFloat(), fpTween(Motion.SLOW), label = "monthBar")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .fpClickable(onClickLabel = "Ver ${m.name} de ${m.year}", enabled = plays > 0, pressedScale = 0.96f) { onPick(m) }
                    .semantics { contentDescription = "${m.name} de ${m.year}: ${timesText(plays)}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val h = this.size.height * (height * grow.value).coerceIn(0f, 1f)
                    val minH = if (plays > 0) 2.dp.toPx() else 1.dp.toPx()
                    val barH = maxOf(h, minH)
                    drawRect(
                        if (on) Fp.Neon else if (plays > 0) Fp.Pink.copy(alpha = 0.45f) else Fp.Track,
                        topLeft = Offset(0f, this.size.height - barH),
                        size = Size(this.size.width, barH),
                    )
                }
                Chakra(m.short.take(1), 10.sp, Modifier.padding(top = 4.dp), color = if (on) Fp.Neon else Fp.MistDim, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StatsBody(state: AppState, view: StatsView, bottom: Dp, sides: Modifier, header: YearMonth?) {
    val s = view.stats
    if (s.totalPlays == 0) {
        Box(sides.padding(top = 16.dp, bottom = bottom)) {
            EmptyState(
                title = when {
                    state.library.isEmpty() -> "Biblioteca vacía"
                    header != null -> "Ningún mes que contar"
                    else -> "Nada en 30 días"
                },
                body = when {
                    state.library.isEmpty() -> "Cuando escuches música, aquí verás tus números."
                    header != null -> "Cuando escuches música, cada mes tendrá su resumen."
                    else -> "No has escuchado nada este mes. Mira lo de siempre."
                },
                ghosts = { Ghost(Mood.Dormido, 52.dp, Modifier.fpFloat()) },
            ) {
                if (state.library.isNotEmpty() && header == null) {
                    PrimaryButton("VER SIEMPRE", { state.statsTab = StatsTab.AllTime }, Modifier.width(200.dp), fill = true)
                }
            }
        }
        return
    }
    val wide = LocalWide.current
    val maxArtist = s.topArtists.maxOfOrNull { it.second } ?: 1
    val songIds = s.topSongs.map { it.id }
    val top = s.topSongs.firstOrNull()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "numbers-" + view.key) {
            BoxWithConstraints(sides.fillMaxWidth()) {
                val columns = if (maxWidth >= 560.dp) 4 else 2
                val cards = listOf(
                    "ESCUCHAS" to s.totalPlays,
                    "MINUTOS" to s.minutes,
                    "CANCIONES" to view.plays.size,
                    view.discovered?.let { "NUEVAS" to it } ?: ("CON NOTA" to s.ratedCount),
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
        if (top != null) {
            item(key = "hero-" + view.key) {
                TopSongHero(top, view.plays[top.id] ?: 0, header, sides.then(itemMotion())) {
                    state.playFrom(songIds, top.id, "ESTADÍSTICAS")
                }
            }
        }
        item(key = "artistsHeader") {
            SectionHeader("01", "ARTISTAS", sides.padding(top = 12.dp, bottom = 2.dp))
        }
        itemsIndexed(s.topArtists, key = { _, a -> "artist-" + view.key + a.first }) { i, (artist, plays) ->
            ArtistRow(i + 1, artist, plays, plays / maxArtist.toFloat(), wide, sides.then(itemMotion()))
        }
        item(key = "songsHeader") {
            SectionHeader("02", "CANCIONES", sides.padding(top = 12.dp, bottom = 2.dp))
        }
        itemsIndexed(s.topSongs, key = { _, song -> "song-" + view.key + song.id }) { i, song ->
            TopSongRow(i + 1, song, view.plays[song.id] ?: 0, sides.then(itemMotion())) {
                state.playFrom(songIds, song.id, "ESTADÍSTICAS")
            }
        }
        if (view.highlights.isNotEmpty()) {
            item(key = "highlightsHeader") {
                SectionHeader("03", "LO MÁS DESTACADO", sides.padding(top = 12.dp, bottom = 2.dp))
            }
            items(view.highlights, key = { "highlight-" + view.key + it.label }) { h ->
                HighlightCard(h, sides.then(itemMotion())) {
                    val id = h.songId
                    if (id != null) state.playFrom(listOf(id), id, "ESTADÍSTICAS")
                }
            }
        }
    }
}

/** Tarjeta de un número grande: sube contando desde 0 al aparecer y al cambiar de periodo. */
@Composable
private fun NumberCard(label: String, value: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val count = remember { Animatable(if (reduce) value.toFloat() else 0f) }
    LaunchedEffect(value, reduce) {
        if (reduce) count.snapTo(value.toFloat()) else count.animateTo(value.toFloat(), tween(Motion.SLOW * 3, easing = Motion.Standard))
    }
    val shape = cut(12.dp)
    Column(
        modifier
            .background(Fp.Card, shape)
            .border(1.dp, Fp.Pink.copy(alpha = 0.25f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Chakra(count.value.toInt().toString(), 28.sp, color = Fp.Neon, weight = FontWeight.Bold)
        Chakra(label, 11.sp, color = Fp.Mist, spacing = 2.sp)
    }
}

/** La canción del periodo, en grande: carátula, título y veces; tocarla la pone a sonar. */
@Composable
private fun TopSongHero(song: Song, plays: Int, month: YearMonth?, modifier: Modifier, onClick: () -> Unit) {
    val shape = cut(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f, onClick = onClick)
            .background(Fp.Neon.copy(alpha = 0.1f), shape)
            .border(1.dp, Fp.Neon.copy(alpha = 0.5f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            Cover(song.coverColor, 72.dp, image = song.cover)
            Ghost(Mood.Feliz, 26.dp, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-12).dp).fpFloat())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Chakra(if (month != null) "TU CANCIÓN DE ${month.name.uppercase()}" else "TU CANCIÓN", 11.sp, color = Fp.Neon, spacing = 2.sp)
            Nunito(song.title, 18.sp, weight = FontWeight.ExtraBold, maxLines = 2)
            SubText(song.artist + " · " + timesText(plays))
        }
    }
}

/** Artista: puesto, nombre, escuchas y una barra de segmentos que crece al aparecer. El n.º 1, con el OwO. */
@Composable
private fun ArtistRow(position: Int, artist: String, plays: Int, fraction: Float, wide: Boolean, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) grow.animateTo(1f, tween(Motion.SLOW * 2, delayMillis = 60 * position, easing = Motion.Standard)) }
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Position(position, Fp.MostPlayedPos.getOrElse(position - 1) { Fp.RankPos[3] }, 20.sp, 24.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Nunito(artist, 14.sp, Modifier.weight(1f, fill = false), weight = FontWeight.ExtraBold, maxLines = 1)
                if (position == 1) Ghost(Mood.Owo, 22.dp, Modifier.offset(y = (-2).dp).fpFloat())
            }
            SegmentBar(count = if (wide) 40 else 24, fraction = fraction * grow.value, height = 4.dp, color = if (position == 1) Fp.Neon else Fp.Pink)
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
        Cover(song.coverColor, 40.dp, image = song.cover)
        Column(Modifier.weight(1f)) {
            Nunito(song.title, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            SubText(song.artist)
        }
        Chakra(timesText(plays), 12.sp, color = Fp.Mist, weight = FontWeight.Medium)
    }
}

/** Lo más destacado del ranking: etiqueta en Neón, título y detalle. Las de una canción, se tocan para escucharla. */
@Composable
private fun HighlightCard(h: Highlight, modifier: Modifier, onClick: () -> Unit) {
    val shape = cut(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (h.songId != null) Modifier.fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f, onClick = onClick) else Modifier)
            .background(Fp.Card, shape)
            .border(1.dp, Fp.Violet.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Chakra(h.label, 10.sp, color = Fp.Neon, spacing = 2.sp)
        Nunito(h.title, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        SubText(h.detail)
    }
}
