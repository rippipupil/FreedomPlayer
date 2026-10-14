package com.freedomplayer.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.GhostSkin
import com.freedomplayer.core.WrappedPeriod
import com.freedomplayer.core.WrappedReport
import com.freedomplayer.core.minutesText
import com.freedomplayer.core.starsText
import com.freedomplayer.core.timesText
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.ChoiceChip
import com.freedomplayer.ui.components.Cover
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.LocalGhostSkin
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.SecondaryButton
import com.freedomplayer.ui.components.cut
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide
import kotlinx.coroutines.launch

/** Las tarjetas del Wrapped, en orden. */
private enum class WrappedCard { Intro, Minutes, TopSong, TopSongs, Artists, Time, Highlights, Ghosts }

/** Los colores de cada tarjeta (degradado de arriba a abajo y el del número grande). */
private class CardLook(val top: Color, val bottom: Color, val accent: Color)

private val looks = mapOf(
    WrappedCard.Intro to CardLook(Color(0xFF3A1240), Color(0xFF110C1B), Fp.Neon),
    WrappedCard.Minutes to CardLook(Color(0xFF173A33), Color(0xFF0E1A1F), Fp.JadeLight),
    WrappedCard.TopSong to CardLook(Color(0xFF4A1530), Color(0xFF150B1C), Fp.NeonSoft),
    WrappedCard.TopSongs to CardLook(Color(0xFF241A4F), Color(0xFF110C1B), Color(0xFFC7C2FF)),
    WrappedCard.Artists to CardLook(Color(0xFF3B2F8F), Color(0xFF120E1C), Fp.Pink),
    WrappedCard.Time to CardLook(Color(0xFF1E1A4A), Color(0xFF0A0812), Color(0xFFA9B2EE)),
    WrappedCard.Highlights to CardLook(Color(0xFF4A3300), Color(0xFF150F0A), Color(0xFFFFD27A)),
    WrappedCard.Ghosts to CardLook(Color(0xFF3A1240), Color(0xFF173A33), Fp.Lamp),
)

/**
 * Tu Wrapped: el resumen de un mes o de un año en tarjetas que se pasan de lado (minutos, tu
 * canción, tus artistas, a qué hora escuchas…). Cada tarjeta se puede guardar como imagen (PC) o
 * compartir (móvil).
 */
@Composable
fun WrappedScreen(state: AppState, modifier: Modifier = Modifier) {
    val pad = screenPadding()
    val sides = Modifier.padding(start = pad.calculateLeftPadding(LayoutDirection.Ltr), end = pad.calculateRightPadding(LayoutDirection.Ltr))
    val periods by remember(state) { derivedStateOf { state.wrappedPeriods() } }
    // Lo normal: el último mes con escuchas (el primero de la lista es su año).
    val period = state.wrappedPeriod?.takeIf { it in periods } ?: periods.firstOrNull { it.month != null }
    val report by remember(state, period) { derivedStateOf { period?.let { state.wrappedReport(it) } } }
    Column(modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
        BackHeader(state, "TU WRAPPED", period?.title ?: "Wrapped", sides.padding(bottom = 10.dp))
        if (periods.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).then(sides).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (p in periods) {
                    ChoiceChip(p.short, p == period) { state.wrappedPeriod = p }
                }
            }
        }
        val r = report
        if (r == null) {
            Box(sides.padding(top = 8.dp)) {
                EmptyState(
                    title = "Aún no hay Wrapped",
                    body = "Cuando escuches música, cada mes y cada año tendrán su resumen en tarjetas.",
                    ghosts = { Ghost(Mood.Dormido, 52.dp, Modifier.fpFloat()) },
                ) {
                    PrimaryButton("VOLVER", { state.back() }, Modifier.width(200.dp), fill = true)
                }
            }
        } else {
            WrappedPager(state, r, Modifier.weight(1f), sides)
        }
    }
}

@Composable
private fun WrappedPager(state: AppState, report: WrappedReport, modifier: Modifier, sides: Modifier) {
    val cards = WrappedCard.entries
    val pager = rememberPagerState { cards.size }
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }
    val scope = rememberCoroutineScope()
    // Otro periodo: se vuelve a la primera tarjeta.
    LaunchedEffect(report.period) { pager.scrollToPage(0) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Tarjeta vertical (como las historias), lo más grande que quepa.
            val cardW = minOf(maxWidth - 48.dp, maxHeight * 0.62f, 420.dp)
            val cardH = minOf(maxHeight, cardW / 0.62f)
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = (maxWidth - cardW) / 2),
                pageSpacing = 14.dp,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val layer = rememberGraphicsLayer()
                DisposableEffect(page, layer) {
                    layers[page] = layer
                    onDispose { if (layers[page] === layer) layers.remove(page) }
                }
                val near by remember { derivedStateOf { pager.currentPage == page } }
                val lift by animateFloatAsState(if (near) 1f else 0.92f, fpTween(Motion.STANDARD), label = "wrappedCard")
                Box(
                    Modifier
                        .size(cardW, cardH)
                        .graphicsLayer {
                            scaleX = lift
                            scaleY = lift
                        }
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                ) {
                    WrappedCardView(state, cards[page], report, active = near)
                }
            }
        }
        Dots(cards.size, pager.currentPage, Modifier.padding(vertical = 12.dp))
        Row(sides.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            if (state.canShareImages) {
                PrimaryButton(
                    if (LocalWide.current) "GUARDAR IMAGEN" else "COMPARTIR",
                    {
                        val page = pager.currentPage
                        val layer = layers[page]
                        if (layer != null) {
                            scope.launch {
                                val image = layer.toImageBitmap()
                                val name = "freedomplayer-wrapped-${report.period.fileTag()}-${page + 1}.png"
                                state.shareImage(image, name)
                            }
                        }
                    },
                    Modifier.weight(1f, fill = false),
                    fill = true,
                )
            }
            if (pager.currentPage < cards.lastIndex) {
                SecondaryButton("SIGUIENTE ›", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.weight(1f, fill = false))
            } else {
                SecondaryButton("FANTASMAS ›", { state.go(Screen.Ghosts) }, Modifier.weight(1f, fill = false))
            }
        }
    }
}

private fun WrappedPeriod.fileTag(): String = if (month == null) "$year" else "$year-${month.toString().padStart(2, '0')}"

/** Los puntitos de debajo: en cuál de las tarjetas estás. */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier) {
    Row(
        modifier.semantics { contentDescription = "Tarjeta ${current + 1} de $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            val on = i == current
            val w by animateFloatAsState(if (on) 18f else 6f, fpTween(Motion.STANDARD), label = "dot")
            Box(Modifier.size(w.dp, 6.dp).background(if (on) Fp.Neon else Fp.Pink.copy(alpha = 0.3f), cut(2.dp)))
        }
    }
}

/** Una tarjeta: su degradado, un fantasma grande de fondo, el contenido y el pie para compartir. */
@Composable
private fun WrappedCardView(state: AppState, card: WrappedCard, r: WrappedReport, active: Boolean) {
    val look = looks.getValue(card)
    val shape = cut(18.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(look.top, look.bottom)), shape)
            .border(1.dp, look.accent.copy(alpha = 0.45f), shape),
    ) {
        Ghost(Mood.Feliz, 220.dp, Modifier.align(Alignment.BottomEnd).offset(x = 60.dp, y = 30.dp).graphicsLayer { alpha = 0.07f })
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (card) {
                WrappedCard.Intro -> IntroCard(r, look)
                WrappedCard.Minutes -> MinutesCard(r, look, active)
                WrappedCard.TopSong -> TopSongCard(r, look)
                WrappedCard.TopSongs -> TopSongsCard(r, look)
                WrappedCard.Artists -> ArtistsCard(r, look)
                WrappedCard.Time -> TimeCard(r, look)
                WrappedCard.Highlights -> HighlightsCard(r, look)
                WrappedCard.Ghosts -> GhostsCard(state, look)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ghost(Mood.Owo, 18.dp)
                Chakra("FREEDOMPLAYER · ${r.period.label}", 10.sp, color = Fp.Mist, spacing = 2.sp)
            }
        }
    }
}

@Composable
private fun CardLabel(text: String, look: CardLook) = Chakra(text, 11.sp, color = look.accent, spacing = 3.sp)

@Composable
private fun ColumnScope.IntroCard(r: WrappedReport, look: CardLook) {
    Spacer(Modifier.height(10.dp))
    CardLabel("FREEDOMPLAYER WRAPPED", look)
    Nunito(r.period.title, 34.sp, weight = FontWeight.ExtraBold, lineHeight = 38.sp, maxLines = 2)
    Nunito("Lo que ha sonado, lo que más te ha gustado y a qué hora te da por escuchar.", 14.sp, color = Fp.Mist, lineHeight = 20.sp)
    Spacer(Modifier.weight(1f))
    Ghost(Mood.Contento, 120.dp, Modifier.align(Alignment.CenterHorizontally).fpFloat(amplitude = 6.dp), skin = LocalGhostSkin.current.withHat())
    Spacer(Modifier.weight(1f))
    Chakra("DESLIZA ›", 12.sp, color = look.accent, spacing = 3.sp)
}

/** En la portada, el fantasma lleva gorro de fiesta (si el suyo no lleva ya algo en la cabeza). */
private fun GhostSkin.withHat(): GhostSkin = if (this == GhostSkin.Clasico) GhostSkin.Fiesta else this

@Composable
private fun ColumnScope.MinutesCard(r: WrappedReport, look: CardLook, active: Boolean) {
    CardLabel("MINUTOS ESCUCHANDO", look)
    Spacer(Modifier.weight(1f))
    CountUp(r.minutes, active, look.accent, 64)
    Nunito(if (r.minutes == 1) "minuto de música" else "minutos de música", 18.sp, weight = FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    StatLine("${r.plays}", if (r.plays == 1) "escucha" else "escuchas", look)
    StatLine("${r.songs}", if (r.songs == 1) "canción distinta" else "canciones distintas", look)
    StatLine("${r.artists}", if (r.artists == 1) "artista" else "artistas", look)
    if (r.newSongs > 0) StatLine("${r.newSongs}", if (r.newSongs == 1) "sonó por primera vez" else "sonaron por primera vez", look)
    Spacer(Modifier.weight(1f))
}

/** Un número que sube desde 0 cuando se ve la tarjeta. */
@Composable
private fun CountUp(value: Int, active: Boolean, color: Color, sizeSp: Int) {
    val reduce = LocalReduceMotion.current
    val count = remember(value) { Animatable(if (reduce) value.toFloat() else 0f) }
    LaunchedEffect(value, active, reduce) {
        if (reduce || !active) {
            if (active) count.snapTo(value.toFloat())
        } else {
            count.animateTo(value.toFloat(), tween(Motion.SLOW * 4, easing = Motion.Standard))
        }
    }
    Chakra(count.value.toInt().toString(), sizeSp.sp, color = color, weight = FontWeight.Bold)
}

@Composable
private fun StatLine(number: String, text: String, look: CardLook) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chakra(number, 20.sp, color = look.accent, weight = FontWeight.Bold)
        Nunito(text, 14.sp, color = Fp.Bone, maxLines = 1)
    }
}

@Composable
private fun ColumnScope.TopSongCard(r: WrappedReport, look: CardLook) {
    val (song, plays) = r.topSongs.first()
    CardLabel(if (r.period.month != null) "TU CANCIÓN DEL MES" else "TU CANCIÓN DEL AÑO", look)
    Spacer(Modifier.weight(1f))
    Box(Modifier.align(Alignment.CenterHorizontally)) {
        Cover(song.coverColor, 170.dp, image = song.cover)
        Ghost(Mood.Owo, 44.dp, Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-18).dp).fpFloat())
    }
    Spacer(Modifier.height(16.dp))
    Nunito(song.title, 24.sp, weight = FontWeight.ExtraBold, maxLines = 2, lineHeight = 28.sp)
    Nunito(song.artist, 15.sp, color = Fp.Mist, maxLines = 1)
    Chakra("SONÓ ${if (plays == 1) "1 VEZ" else "$plays VECES"}", 13.sp, color = look.accent, spacing = 2.sp)
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.TopSongsCard(r: WrappedReport, look: CardLook) {
    CardLabel("TUS 5 MÁS ESCUCHADAS", look)
    Spacer(Modifier.height(6.dp))
    r.topSongs.forEachIndexed { i, (song, plays) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chakra("${i + 1}", 22.sp, Modifier.width(22.dp), color = if (i == 0) look.accent else Fp.Mist, weight = FontWeight.Bold)
            Cover(song.coverColor, 46.dp, image = song.cover)
            Column(Modifier.weight(1f)) {
                Nunito(song.title, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                Nunito(song.artist, 12.sp, color = Fp.Mist, maxLines = 1)
            }
            Chakra(timesText(plays), 12.sp, color = Fp.Mist)
        }
    }
}

@Composable
private fun ColumnScope.ArtistsCard(r: WrappedReport, look: CardLook) {
    val (top, plays) = r.topArtists.first()
    CardLabel("TU ARTISTA", look)
    Nunito(top, 30.sp, weight = FontWeight.ExtraBold, maxLines = 2, lineHeight = 34.sp)
    Chakra("SONÓ ${if (plays == 1) "1 VEZ" else "$plays VECES"}", 13.sp, color = look.accent, spacing = 2.sp)
    Spacer(Modifier.height(10.dp))
    val max = r.topArtists.maxOf { it.second }.coerceAtLeast(1)
    r.topArtists.forEachIndexed { i, (name, n) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Nunito("${i + 1}. $name", 14.sp, Modifier.weight(1f), weight = FontWeight.Bold, maxLines = 1)
                Chakra(timesText(n), 12.sp, color = Fp.Mist)
            }
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                drawRect(Fp.Track, size = this.size)
                drawRect(if (i == 0) look.accent else Fp.Pink.copy(alpha = 0.6f), size = Size(this.size.width * n / max, this.size.height))
            }
        }
    }
}

@Composable
private fun ColumnScope.TimeCard(r: WrappedReport, look: CardLook) {
    val peak = r.byHour.indices.maxBy { r.byHour[it] }
    CardLabel("TU HORA", look)
    Spacer(Modifier.weight(1f))
    Ghost(if (r.time.hours.first < 6) Mood.Dormido else Mood.Guino, 72.dp, Modifier.align(Alignment.CenterHorizontally).fpFloat())
    Spacer(Modifier.height(10.dp))
    Nunito(r.time.label, 26.sp, weight = FontWeight.ExtraBold, lineHeight = 30.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Nunito(r.time.blurb, 14.sp, color = Fp.Mist, lineHeight = 20.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    val max = r.byHour.max().coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val gap = 2.dp.toPx()
        val w = (this.size.width - gap * 23) / 24
        r.byHour.forEachIndexed { h, n ->
            val bar = maxOf(this.size.height * n / max, 2.dp.toPx())
            drawRect(
                if (h == peak) look.accent else Fp.Pink.copy(alpha = if (n > 0) 0.5f else 0.15f),
                topLeft = Offset(h * (w + gap), this.size.height - bar),
                size = Size(w, bar),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Chakra("0 H", 10.sp, color = Fp.MistDim)
        Spacer(Modifier.weight(1f))
        Chakra("12 H", 10.sp, color = Fp.MistDim)
        Spacer(Modifier.weight(1f))
        Chakra("23 H", 10.sp, color = Fp.MistDim)
    }
    Chakra("SOBRE TODO A LAS ${peak.toString().padStart(2, '0')}:00", 12.sp, color = look.accent, spacing = 2.sp)
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.HighlightsCard(r: WrappedReport, look: CardLook) {
    CardLabel("LO MÁS DESTACADO", look)
    Spacer(Modifier.height(6.dp))
    val best = r.bestRated
    if (best != null) {
        Highlight("LA MEJOR PUNTUADA", best.title, "${best.artist} · ${starsText(best.score!!)}", look) {
            Cover(best.coverColor, 52.dp, image = best.cover)
        }
    }
    Highlight(
        "TU RACHA",
        if (r.streakDays == 1) "1 día" else "${r.streakDays} días seguidos",
        "escuchando música sin faltar ni uno",
        look,
    ) { Ghost(Mood.Feliz, 46.dp) }
    Highlight(
        "DESCUBRIMIENTOS",
        if (r.newSongs == 1) "1 canción nueva" else "${r.newSongs} canciones nuevas",
        "que sonaron por primera vez",
        look,
    ) { Ghost(Mood.Sorpresa, 46.dp) }
    Highlight("EN TOTAL", minutesText(r.minutes), "de música, que se dice pronto", look) { Ghost(Mood.Uwu, 46.dp) }
}

@Composable
private fun Highlight(label: String, title: String, detail: String, look: CardLook, art: @Composable BoxScope.() -> Unit) {
    val shape = cut(10.dp)
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.22f), shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center, content = art)
        Column(Modifier.weight(1f)) {
            Chakra(label, 10.sp, color = look.accent, spacing = 2.sp)
            Nunito(title, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            Nunito(detail, 11.sp, color = Fp.Mist, maxLines = 1)
        }
    }
}

@Composable
private fun ColumnScope.GhostsCard(state: AppState, look: CardLook) {
    val facts by remember(state) { derivedStateOf { state.ghostFacts() } }
    val owned = state.ownedGhosts(facts)
    CardLabel("TU COLECCIÓN", look)
    Nunito("${owned.size} de ${GhostSkin.entries.size} fantasmas", 26.sp, weight = FontWeight.ExtraBold, lineHeight = 30.sp)
    Spacer(Modifier.height(8.dp))
    GhostSkin.entries.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { skin ->
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (skin in owned) {
                        Ghost(Mood.Feliz, 46.dp, skin = skin)
                    } else {
                        Ghost(Mood.Dormido, 46.dp, Modifier.graphicsLayer { alpha = 0.2f }, skin = skin)
                    }
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Nunito("Sigue escuchando: hay más fantasmas esperando.", 14.sp, color = Fp.Mist, lineHeight = 20.sp)
}
