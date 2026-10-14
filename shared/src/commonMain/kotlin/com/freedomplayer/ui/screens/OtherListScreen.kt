package com.freedomplayer.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.OtherList
import com.freedomplayer.core.songCount
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.explain
import com.freedomplayer.ui.AddTarget
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.OtherListState
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.InLibraryBadge
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.SecondaryButton
import com.freedomplayer.ui.components.SegmentBar
import com.freedomplayer.ui.components.StackedCover
import com.freedomplayer.ui.components.fpBob
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/**
 * Una lista de otra persona (YouTube Music o YouTube): sus canciones, cada una con su vista previa
 * en la carátula y su «+» para añadir solo esa, y «+ AÑADIR TODAS» por si se quiere entera.
 */
@Composable
fun OtherListScreen(state: AppState, url: String, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val pad = screenPadding()
    // En el móvil, a 46 dp de los bordes, como en Buscar: la pestaña derecha no pisa los «+».
    val listPad = if (wide) {
        pad
    } else {
        PaddingValues(start = 46.dp, end = 46.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
    }
    val st = state.otherList?.takeIf { it.url == url }
    val list = st?.list
    val label = if ("music.youtube.com" in url) "LISTA DE YOUTUBE MUSIC" else "LISTA DE YOUTUBE"
    LazyColumn(modifier.fillMaxSize(), contentPadding = listPad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "header") {
            BackHeader(state, label, st?.info?.title ?: "Lista", Modifier.padding(bottom = 18.dp))
        }
        if (st == null) {
            item(key = "gone") {
                EmptyState(
                    title = "Lista cerrada",
                    body = "Búscala otra vez en Buscar › Listas o pega su enlace.",
                    modifier = itemMotion(),
                    ghosts = { Ghost(Mood.Sorpresa, 52.dp, Modifier.fpBob()) },
                ) {
                    PrimaryButton("BUSCAR", { state.go(Screen.Search) }, Modifier.width(200.dp), fill = true)
                }
            }
        } else {
            item(key = "summary") {
                Summary(state, st, Modifier.padding(bottom = 14.dp))
            }
            when {
                list != null && list.tracks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = "Lista vacía",
                        body = "Esta lista no tiene canciones, o YouTube no las enseña.",
                        modifier = itemMotion(),
                        ghosts = { Ghost(Mood.Sorpresa, 52.dp, Modifier.fpBob()) },
                    )
                }
                list != null -> {
                    item(key = "hint") {
                        HintNote(
                            "Toca la carátula para escucharla antes y «+» para añadir solo esa.",
                            Modifier.padding(bottom = 10.dp),
                        )
                    }
                    items(list.tracks, key = { it.id }) { r ->
                        ResultRow(state, r, itemMotion())
                    }
                }
                st.error != null && !st.loading -> item(key = "error") {
                    Failed(state, st.error ?: FailureKind.Unknown, itemMotion())
                }
                !state.online -> item(key = "offline") {
                    EmptyState(
                        title = "Sin conexión",
                        body = "La lista se abre cuando haya internet.",
                        modifier = itemMotion(),
                        ghosts = { Ghost(Mood.Triste, 52.dp, Modifier.fpBob()) },
                    )
                }
                else -> item(key = "loading") {
                    SkeletonRows(48.dp, 48.dp, gap = 14.dp)
                }
            }
        }
    }
}

/** Carátulas apiladas, «QUIÉN · N CANCIONES» y «+ AÑADIR TODAS» (o cómo va esa descarga). */
@Composable
private fun Summary(state: AppState, st: OtherListState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val info = st.info
    val list = st.list
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StackedCover(
            info?.coverColor ?: 0xFF3B2F8F,
            front = if (wide) 120.dp else 92.dp,
            back = if (wide) 112.dp else 86.dp,
            backX = if (wide) 14.dp else 10.dp,
            frontY = if (wide) 12.dp else 9.dp,
            image = info?.cover ?: list?.tracks?.firstOrNull { it.cover != null }?.cover,
            backImage = list?.tracks?.filter { it.cover != null }?.getOrNull(1)?.cover,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val count = list?.tracks?.size ?: info?.count
            Chakra(
                listOfNotNull(info?.owner?.uppercase()?.ifBlank { null }, count?.let { songCount(it) }).joinToString(" · ").ifEmpty { "LISTA" },
                11.sp,
                color = Fp.Mist,
                spacing = 1.5.sp,
            )
            if (list != null && list.tracks.isNotEmpty()) AddAll(state, list)
        }
    }
}

@Composable
private fun AddAll(state: AppState, list: OtherList) {
    val job = state.otherListJob(list.info.url)
    val all = list.tracks.all { state.librarySongFor(it) != null }
    Crossfade(
        targetState = when {
            job != null -> 1
            all -> 2
            else -> 0
        },
        animationSpec = tween(Motion.STANDARD),
        label = "addAll",
    ) { mode ->
        when (mode) {
            1 -> {
                val j = state.otherListJob(list.info.url)
                Column(Modifier.heightIn(min = 44.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    Chakra(
                        if (j == null) "LISTO" else "DESCARGANDO ${minOf(j.done + 1, j.total)} DE ${j.total}",
                        11.sp,
                        color = Fp.JadeLight,
                        spacing = 1.5.sp,
                    )
                    SegmentBar(
                        count = (j?.total ?: 12).coerceIn(4, 24),
                        fraction = j?.fraction ?: 1f,
                        modifier = Modifier.widthIn(max = 180.dp),
                        color = Fp.Jade,
                        height = 3.dp,
                    )
                }
            }
            2 -> Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                InLibraryBadge(text = "TODAS EN TU BIBLIO")
            }
            else -> PrimaryButton(
                "+ AÑADIR TODAS",
                { state.requestAdd(AddTarget.WholeList(list)) },
                height = 40.dp,
                cutSize = 10.dp,
                fontSize = 12.sp,
                spacing = 1.5.sp,
                horizontalPadding = 14.dp,
            )
        }
    }
}

/** No se ha podido abrir: por qué, y «REINTENTAR». */
@Composable
private fun Failed(state: AppState, kind: FailureKind, modifier: Modifier = Modifier) {
    EmptyState(
        title = "No se pudo abrir la lista",
        body = when (kind) {
            FailureKind.Unavailable -> "Esa lista es privada o ya no existe. Si es tuya, hazla pública u oculta en YouTube y vuelve a probar."
            else -> kind.explain()
        },
        modifier = modifier,
        ghosts = { Ghost(Mood.Triste, 52.dp, Modifier.fpBob()) },
    ) {
        PrimaryButton("REINTENTAR", { state.retryOtherList() }, Modifier.width(200.dp), fill = true)
        SecondaryButton("VOLVER", { state.back() }, Modifier.width(200.dp))
    }
}
