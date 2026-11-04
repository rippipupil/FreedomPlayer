package com.freedomplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.PlaylistOrder
import com.freedomplayer.core.PlaylistSort
import com.freedomplayer.core.songCount
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.ChoiceChip
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.IconButton
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.ScreenHeader
import com.freedomplayer.ui.components.SecondaryButton
import com.freedomplayer.ui.components.SongRow
import com.freedomplayer.ui.components.StackedCover
import com.freedomplayer.ui.components.fpBob
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/** Cabecera de las pantallas que no están en el menú: «‹» para volver y el título. */
@Composable
internal fun BackHeader(state: AppState, label: String, title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(
            FpIcons.Back,
            "Volver",
            { state.back() },
            Modifier.offset(x = (-12).dp, y = 2.dp),
            iconSize = 22.dp,
            color = Fp.Bone,
        )
        ScreenHeader(label, title, Modifier.offset(x = (-12).dp).weight(1f))
    }
}

/**
 * Detalle de una playlist: REPRODUCIR y aleatorio (el icono de las dos líneas cruzadas), el orden
 * (por fecha o por nota, con la flecha para darle la vuelta) y sus canciones (tocar una la pone a
 * sonar y la cola sigue en ese orden).
 */
@Composable
fun PlaylistScreen(state: AppState, id: String, modifier: Modifier = Modifier) {
    val playlist = state.playlist(id)
    val pad = screenPadding()
    if (playlist == null) {
        Column(modifier.fillMaxSize().padding(pad)) {
            BackHeader(state, "PLAYLIST", "No existe")
        }
        return
    }
    val wide = LocalWide.current
    val songs = state.playlistSongs(playlist).let { if (state.library.isEmpty()) emptyList() else it }
    val ids = songs.map { it.id }
    val minutes = songs.sumOf { it.durationSec } / 60
    val current = state.currentSong?.id
    LazyColumn(modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "header") {
            BackHeader(state, "PLAYLIST", playlist.name, Modifier.padding(bottom = 18.dp))
        }
        item(key = "summary") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                StackedCover(
                    playlist.coverColor,
                    front = if (wide) 120.dp else 92.dp,
                    back = if (wide) 112.dp else 86.dp,
                    backX = if (wide) 14.dp else 10.dp,
                    frontY = if (wide) 12.dp else 9.dp,
                    image = songs.firstOrNull { it.cover != null }?.cover,
                    backImage = songs.filter { it.cover != null }.getOrNull(1)?.cover ?: songs.firstOrNull { it.cover != null }?.cover,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chakra(
                        if (songs.isEmpty()) songCount(0) else "${songCount(songs.size)} · $minutes MIN",
                        11.sp,
                        color = Fp.Mist,
                        spacing = 1.5.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            "REPRODUCIR",
                            { state.playPlaylist(playlist.id, shuffle = false) },
                            height = 40.dp,
                            cutSize = 10.dp,
                            fontSize = 12.sp,
                            spacing = 1.5.sp,
                            horizontalPadding = 14.dp,
                            enabled = songs.isNotEmpty(),
                            icon = FpIcons.Play,
                        )
                        SecondaryButton(
                            "Aleatorio",
                            { state.playPlaylist(playlist.id, shuffle = true) },
                            height = 40.dp,
                            enabled = songs.isNotEmpty(),
                            icon = FpIcons.Shuffle,
                        )
                    }
                }
            }
        }
        if (songs.size > 1) {
            item(key = "order") {
                OrderBar(state, state.settings.playlistOrder, Modifier.padding(bottom = 8.dp))
            }
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "Playlist vacía",
                    body = "Añade canciones con «+» en Buscar y elige esta playlist.",
                    modifier = itemMotion(),
                    ghosts = { Ghost(Mood.Sorpresa, 52.dp, Modifier.fpBob()) },
                ) {
                    PrimaryButton("BUSCAR", { state.go(Screen.Search) }, Modifier.width(200.dp), fill = true)
                }
            }
        }
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                playing = song.id == current,
                onClick = { state.playFrom(ids, song.id, playlist.name.uppercase()) },
                modifier = itemMotion(),
                coverSize = if (wide) 40.dp else 36.dp,
            )
        }
    }
}

/** «ORDEN»: fecha o nota, y la flecha que pasa de mayor a menor a al revés. */
@Composable
private fun OrderBar(state: AppState, order: PlaylistOrder, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chakra("ORDEN", 11.sp, color = Fp.Mist, spacing = 1.5.sp)
        for (sort in PlaylistSort.entries) {
            ChoiceChip(sort.label, order.by == sort) { state.sortPlaylistsBy(sort) }
        }
        Spacer(Modifier.weight(1f))
        val (now, then) = when (order.by) {
            PlaylistSort.Added -> if (order.descending) "RECIENTES" to "las más antiguas" else "ANTIGUAS" to "las más recientes"
            PlaylistSort.Score -> if (order.descending) "MEJORES" to "las de menos nota" else "PEORES" to "las de más nota"
        }
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .fpClickable(onClickLabel = "Poner primero $then", onClick = { state.flipPlaylistOrder() })
                .semantics { contentDescription = "Primero ${now.lowercase()}" }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FpIconView(if (order.descending) FpIcons.SortDown else FpIcons.SortUp, 16.dp, Fp.Pink)
            Chakra(now, 11.sp, color = Fp.Pink, spacing = 1.5.sp)
        }
    }
}
