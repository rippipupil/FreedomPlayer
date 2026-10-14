package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.MatchKind
import com.freedomplayer.core.TrackMatch
import com.freedomplayer.core.formatTime
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.ImportPhase
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.SearchTab
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.FpTextField
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.IconButton
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.SecondaryButton
import com.freedomplayer.ui.components.SectionHeader
import com.freedomplayer.ui.components.SegmentBar
import com.freedomplayer.ui.components.StateCard
import com.freedomplayer.ui.components.TextAction
import com.freedomplayer.ui.components.cut
import com.freedomplayer.ui.components.fpBob
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp

/**
 * Importar de Spotify (lo mismo que «Pegar playlist de Spotify» en Buscar): se pega el enlace,
 * se crea la playlist con lo que coincide seguro y las coincidencias dudosas se confirman aquí.
 */
@Composable
fun SpotifyScreen(state: AppState, modifier: Modifier = Modifier) {
    val sp = state.spotify
    val pad = screenPadding()
    val reduce = LocalReduceMotion.current
    Column(modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        BackHeader(
            state,
            "SPOTIFY → YOUTUBE MUSIC",
            "Importar",
            Modifier.padding(start = pad.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), end = pad.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), bottom = 6.dp),
        )
        AnimatedContent(
            targetState = sp.phase,
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                } else {
                    fadeIn(tween(Motion.SLOW, delayMillis = 60)) togetherWith fadeOut(tween(Motion.QUICK))
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "importPhase",
        ) { phase ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = pad.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = pad.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = 12.dp,
                    bottom = pad.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (phase) {
                    ImportPhase.Paste -> pasteStep(state)
                    ImportPhase.Matching -> matchingStep(state)
                    ImportPhase.Done -> doneStep(state)
                }
            }
        }
    }
}

private fun LazyListScope.pasteStep(state: AppState) {
    val sp = state.spotify
    item(key = "hint") {
        HintNote(
            "En Spotify: Compartir → Copiar enlace (la playlist tiene que ser pública). Pégalo aquí: se crea la " +
                "playlist y cada canción se busca en YouTube Music. Las que no estén claras te las enseñamos para que elijas.",
        )
    }
    item(key = "field") {
        LinkField(state)
    }
    item(key = "error") {
        AnimatedVisibility(
            visible = sp.error != null,
            enter = expandVertically(tween(Motion.STANDARD, easing = Motion.Standard)) + fadeIn(tween(Motion.STANDARD)),
            exit = shrinkVertically(tween(Motion.QUICK)) + fadeOut(tween(Motion.QUICK)),
        ) {
            StateCard(Mood.Triste, "No se puede importar", sp.error ?: "", edge = Fp.Neon, bob = true)
        }
    }
    item(key = "go") {
        PrimaryButton(
            if (state.online) "IMPORTAR" else "SIN CONEXIÓN",
            { state.startSpotifyImport() },
            Modifier.fillMaxWidth(),
            fill = true,
            enabled = sp.link.isNotBlank() && state.online,
            icon = FpIcons.Link,
        )
    }
}

/** Campo del enlace: borde jade, icono de cadena y pegar con el teclado. */
@Composable
private fun LinkField(state: AppState) {
    val sp = state.spotify
    val shape = cut(12.dp)
    val edge by animateColorAsState(if (sp.error != null) Fp.Neon else Fp.Jade, fpTween(), label = "linkEdge")
    FpTextField(
        value = sp.link,
        onValueChange = {
            sp.link = it
            sp.error = null
        },
        placeholder = "https://open.spotify.com/playlist/…",
        label = "Enlace de la playlist de Spotify",
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Fp.Panel, shape)
            .border(1.dp, edge, shape)
            .padding(start = 14.dp, end = 4.dp),
        textSize = 14.sp,
        imeAction = ImeAction.Go,
        onSubmit = { if (sp.link.isNotBlank()) state.startSpotifyImport() },
        leading = { FpIconView(FpIcons.Link, 18.dp, Fp.JadeLight) },
    )
}

private fun LazyListScope.matchingStep(state: AppState) {
    val sp = state.spotify
    item(key = "matching") {
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Ghost(Mood.Owo, 64.dp, Modifier.fpFloat())
            // Primero se lee la playlist de Spotify; luego se busca cada canción.
            Crossfade(targetState = sp.total == 0, animationSpec = tween(Motion.STANDARD), label = "matchingTitle") { reading ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Nunito(if (reading) "Leyendo la playlist de Spotify…" else "Buscando en YouTube Music…", 15.sp, weight = FontWeight.ExtraBold)
                    Chakra(if (reading) "UN MOMENTO" else "${sp.checked} DE ${sp.total}", 12.sp, color = Fp.Mist, spacing = 2.sp)
                }
            }
            SegmentBar(
                count = sp.total.coerceIn(4, 30),
                fraction = if (sp.total == 0) 0f else sp.checked / sp.total.toFloat(),
                modifier = Modifier.width(240.dp),
                height = 4.dp,
            )
        }
    }
}

private fun LazyListScope.doneStep(state: AppState) {
    val sp = state.spotify
    val matches = sp.matches.toList()
    val sure = matches.count { it.kind == MatchKind.Segura }
    val doubtful = matches.withIndex().filter { it.value.kind == MatchKind.Dudosa }
    val missing = matches.filter { it.kind == MatchKind.SinCoincidencia }
    val playlist = state.playlist(sp.playlistId)
    item(key = "summary") {
        StateCard(
            Mood.Feliz,
            "«${playlist?.name ?: "Playlist"}» creada",
            "$sure seguras · ${doubtful.size} dudosas · ${missing.size} sin coincidencia. Lo seguro ya se está descargando.",
            edge = Fp.Jade,
        )
    }
    val note = sp.note
    if (note != null) {
        item(key = "note") {
            HintNote(note)
        }
    }
    if (doubtful.isNotEmpty()) {
        item(key = "doubtHeader") {
            SectionHeader("01", "CONFIRMA LAS DUDOSAS", Modifier.padding(top = 6.dp))
        }
        for ((index, match) in doubtful) {
            item(key = "doubt-$index") {
                DoubtRow(state, index, match, itemMotion())
            }
        }
    }
    if (missing.isNotEmpty()) {
        item(key = "missingHeader") {
            SectionHeader(if (doubtful.isEmpty()) "01" else "02", "SIN COINCIDENCIA", Modifier.padding(top = 6.dp))
        }
        for (m in missing) {
            item(key = "missing-${m.track.title}") {
                MissingRow(state, m, itemMotion())
            }
        }
    }
    item(key = "actions") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val pid = sp.playlistId
            if (pid != null) {
                PrimaryButton("VER PLAYLIST", { state.go(Screen.PlaylistDetail(pid)) }, Modifier.fillMaxWidth(), fill = true)
            }
            SecondaryButton("IMPORTAR OTRA", { sp.reset() }, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Coincidencia dudosa: lo de Spotify arriba y lo encontrado debajo, con ✓ (añadir) y ✗ (descartar).
 * Una vez elegida, se queda la etiqueta.
 */
@Composable
private fun DoubtRow(state: AppState, index: Int, match: TrackMatch, modifier: Modifier = Modifier) {
    val decision = state.spotify.decisions[index]
    val r = match.result
    Row(
        modifier
            .fillMaxWidth()
            .background(Fp.Card.copy(alpha = 0.9f))
            .drawBehind { drawRect(if (decision == true) Fp.Jade else Fp.Violet, size = Size(2.dp.toPx(), this.size.height)) }
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Chakra("SPOTIFY", 10.sp, color = Fp.MistDim, spacing = 1.5.sp)
            Nunito("${match.track.title} — ${match.track.artist}", 14.sp, weight = FontWeight.ExtraBold, maxLines = 2)
            Chakra("YOUTUBE MUSIC", 10.sp, Modifier.padding(top = 4.dp), color = Fp.MistDim, spacing = 1.5.sp)
            Nunito(
                if (r != null) "${r.title} — ${r.artist} · ${formatTime(r.durationSec)}" else "—",
                13.sp,
                color = Fp.Mist,
                maxLines = 2,
            )
        }
        Crossfade(targetState = decision, animationSpec = tween(Motion.STANDARD), label = "decision") { d ->
            when (d) {
                null -> Row {
                    IconButton(FpIcons.Check, "Sí, es esta: añadir", { state.decideMatch(index, true) }, iconSize = 18.dp, color = Fp.JadeLight)
                    IconButton(FpIcons.Close, "No es esta: descartar", { state.decideMatch(index, false) }, iconSize = 16.dp, color = Fp.Mist)
                }
                true -> Box(Modifier.heightIn(min = 44.dp).padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    Chakra("AÑADIDA", 11.sp, color = Fp.JadeLight, spacing = 1.5.sp)
                }
                false -> Box(Modifier.heightIn(min = 44.dp).padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    Chakra("DESCARTADA", 11.sp, color = Fp.MistDim, spacing = 1.5.sp)
                }
            }
        }
    }
}

/** Sin coincidencia: se puede buscar a mano (abre Buscar con el título y el artista). */
@Composable
private fun MissingRow(state: AppState, match: TrackMatch, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Fp.Pink.copy(alpha = 0.12f), topLeft = Offset(0f, this.size.height - 1.dp.toPx()), size = Size(this.size.width, 1.dp.toPx()))
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ghost(Mood.Sorpresa, 28.dp, Modifier.fpBob())
        Column(Modifier.weight(1f)) {
            Nunito(match.track.title, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            Nunito(match.track.artist, 12.sp, color = Fp.Mist, maxLines = 1)
        }
        TextAction(
            "BUSCAR",
            {
                state.query = "${match.track.title} ${match.track.artist}"
                state.searchTab = SearchTab.Videos
                state.go(Screen.Search)
            },
            color = Fp.Neon,
            spacing = 1.5.sp,
        )
    }
}
