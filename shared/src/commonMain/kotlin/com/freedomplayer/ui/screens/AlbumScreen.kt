package com.freedomplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.AlbumResult
import com.freedomplayer.core.songCount
import com.freedomplayer.data.explain
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.SecondaryButton
import com.freedomplayer.ui.components.StackedCover
import com.freedomplayer.ui.components.fpBob
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/**
 * Un álbum del buscador por dentro: sus canciones en orden, cada una con su vista previa en la
 * carátula y su «+» para añadir solo esa (a la biblioteca o a una playlist), y «+ ÁLBUM ENTERO».
 */
@Composable
fun AlbumScreen(state: AppState, key: String, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val pad = screenPadding()
    // En el móvil, a 46 dp de los bordes, como en Buscar: la pestaña derecha no pisa los «+».
    val listPad = if (wide) {
        pad
    } else {
        PaddingValues(start = 46.dp, end = 46.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
    }
    val album = state.openedAlbum?.takeIf { it.key == key }
    val tracks = album?.let { state.albumTracks[it.key] }
    val failed = album?.let { state.albumFailed[it.key] }
    LazyColumn(modifier.fillMaxSize(), contentPadding = listPad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "header") {
            BackHeader(state, "ÁLBUM", album?.title ?: "Álbum", Modifier.padding(bottom = 18.dp))
        }
        if (album == null) {
            item(key = "gone") {
                EmptyState(
                    title = "Álbum cerrado",
                    body = "Búscalo otra vez en Buscar › Álbumes.",
                    modifier = itemMotion(),
                    ghosts = { Ghost(Mood.Sorpresa, 52.dp, Modifier.fpBob()) },
                ) {
                    PrimaryButton("BUSCAR", { state.go(Screen.Search) }, Modifier.width(200.dp), fill = true)
                }
            }
        } else {
            item(key = "summary") {
                Summary(state, album, tracks?.size, Modifier.padding(bottom = 14.dp))
            }
            when {
                tracks != null && tracks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = "Álbum vacío",
                        body = "YouTube Music no enseña canciones de este álbum.",
                        modifier = itemMotion(),
                        ghosts = { Ghost(Mood.Sorpresa, 52.dp, Modifier.fpBob()) },
                    )
                }
                tracks != null -> {
                    item(key = "hint") {
                        HintNote(
                            "Toca la carátula para escucharla antes y «+» para añadir solo esa.",
                            Modifier.padding(bottom = 10.dp),
                        )
                    }
                    items(tracks, key = { it.id }) { r ->
                        ResultRow(state, r, itemMotion())
                    }
                }
                failed != null -> item(key = "error") {
                    EmptyState(
                        title = "No se pudo abrir el álbum",
                        body = failed.explain(),
                        modifier = itemMotion(),
                        ghosts = { Ghost(Mood.Triste, 52.dp, Modifier.fpBob()) },
                    ) {
                        PrimaryButton("REINTENTAR", { state.retryAlbum() }, Modifier.width(200.dp), fill = true)
                        SecondaryButton("VOLVER", { state.back() }, Modifier.width(200.dp))
                    }
                }
                !state.online -> item(key = "offline") {
                    EmptyState(
                        title = "Sin conexión",
                        body = "Las canciones del álbum se ven cuando haya internet.",
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

/** Carátula, «ARTISTA · AÑO · N CANCIONES» y «+ ÁLBUM ENTERO» (o cómo va esa descarga). */
@Composable
private fun Summary(state: AppState, album: AlbumResult, count: Int?, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StackedCover(
            album.coverColor,
            front = if (wide) 120.dp else 92.dp,
            back = if (wide) 112.dp else 86.dp,
            backX = if (wide) 14.dp else 10.dp,
            frontY = if (wide) 12.dp else 9.dp,
            image = album.cover,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chakra(
                listOfNotNull(album.artist.uppercase().ifBlank { null }, album.year, count?.let { songCount(it) }).joinToString(" · ").ifEmpty { "ÁLBUM" },
                11.sp,
                color = Fp.Mist,
                spacing = 1.5.sp,
            )
            if (count != null && count > 0) AlbumAction(state, album)
        }
    }
}
