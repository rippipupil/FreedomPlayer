package com.freedomplayer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.GhostCollection
import com.freedomplayer.core.GhostSkin
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.SegmentBar
import com.freedomplayer.ui.components.cut
import com.freedomplayer.ui.components.drawGhost
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp

/**
 * Los fantasmas coleccionables: arriba el que llevas puesto; debajo todos, los ganados en color
 * (tocar uno te lo pone) y los que faltan en sombra, con lo que piden y cuánto llevas.
 */
@Composable
fun GhostsScreen(state: AppState, modifier: Modifier = Modifier) {
    val facts by remember(state) { derivedStateOf { state.ghostFacts() } }
    val owned = state.ownedGhosts(facts)
    val worn = state.settings.ghostSkin
    LazyColumn(modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            BackHeader(state, "COLECCIÓN · ${owned.size} DE ${GhostSkin.entries.size}", "Fantasmas", Modifier.padding(bottom = 6.dp))
        }
        item(key = "worn") {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Ghost(Mood.Contento, 88.dp, Modifier.fpFloat(), skin = worn)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chakra("LLEVAS PUESTO", 11.sp, color = Fp.Mist, spacing = 2.sp)
                    Nunito(worn.label, 22.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                    Nunito("Sale en toda la app: avisos, búsquedas, pantallas vacías…", 12.sp, color = Fp.Mist, lineHeight = 17.sp)
                }
            }
        }
        item(key = "hint") {
            HintNote("Se ganan escuchando, puntuando y llenando tu biblioteca. Toca uno ganado para ponértelo.")
        }
        item(key = "grid") {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                val columns = when {
                    maxWidth >= 760.dp -> 5
                    maxWidth >= 520.dp -> 3
                    else -> 2
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostSkin.entries.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { skin ->
                                GhostCard(
                                    skin = skin,
                                    owned = skin in owned,
                                    worn = skin == worn,
                                    progress = GhostCollection.progress(skin, facts),
                                    modifier = Modifier.weight(1f),
                                ) { state.chooseGhost(skin) }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** Un fantasma de la colección: ganado (se toca para ponérselo) o en sombra con lo que falta. */
@Composable
private fun GhostCard(skin: GhostSkin, owned: Boolean, worn: Boolean, progress: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = cut(12.dp)
    val line = when {
        worn -> Fp.Neon
        owned -> Fp.Pink.copy(alpha = 0.4f)
        else -> Fp.Pink.copy(alpha = 0.15f)
    }
    val fill by animateFloatAsState(if (skin.goal == 0) 1f else (progress / skin.goal.toFloat()).coerceIn(0f, 1f), fpTween(Motion.SLOW), label = "ghostGoal")
    Column(
        modifier
            .fpClickable(onClickLabel = "Ponérmelo", enabled = owned && !worn, pressedScale = 0.96f, onClick = onClick)
            .background(if (worn) Fp.Neon.copy(alpha = 0.1f) else Fp.Card, shape)
            .border(1.dp, line, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Fantasma ${skin.label}"
                stateDescription = when {
                    worn -> "Puesto"
                    owned -> "Ganado"
                    else -> "Sin ganar: ${skin.hint} Llevas ${progress.coerceAtMost(skin.goal)} de ${skin.goal}."
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (owned) {
                Ghost(Mood.Feliz, 64.dp, skin = skin)
            } else {
                Canvas(Modifier.size(64.dp).graphicsLayer { alpha = 0.22f }) { drawGhost(Mood.Dormido, skin = skin) }
                Chakra("?", 22.sp, color = Fp.Mist, weight = FontWeight.Bold)
            }
        }
        Nunito(skin.label, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1, align = TextAlign.Center)
        Nunito(skin.hint, 11.sp, color = Fp.Mist, lineHeight = 15.sp, align = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        when {
            worn -> Chakra("PUESTO", 10.sp, color = Fp.Neon, spacing = 2.sp)
            owned -> Chakra("GANADO", 10.sp, color = Fp.JadeLight, spacing = 2.sp)
            else -> {
                SegmentBar(count = 10, fraction = fill, height = 3.dp, color = Fp.Pink)
                Chakra("${progress.coerceAtMost(skin.goal)} / ${skin.goal}", 10.sp, color = Fp.Mist, spacing = 1.sp)
            }
        }
    }
}
