package com.freedomplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Song
import com.freedomplayer.core.formatTime
import com.freedomplayer.core.songCount
import com.freedomplayer.ui.theme.Fp

/**
 * Fila de canción (paneles, playlists): carátula, título, artista y duración.
 * La que suena va con raya rosa, fondo rosado y título en rosa. Tocarla la pone a sonar.
 */
@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverSize: Dp = 36.dp,
    verticalPadding: Dp = 7.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val edge by animateColorAsState(if (playing) Fp.Neon else Color.Transparent, fpTween(), label = "rowEdge")
    val bg by animateColorAsState(if (playing) Fp.Neon.copy(alpha = 0.1f) else Color.Transparent, fpTween(), label = "rowBg")
    val titleColor by animateColorAsState(if (playing) Fp.NeonSoft else Fp.Bone, fpTween(), label = "rowTitle")
    Row(
        modifier
            .fillMaxWidth()
            .fpClickable(onClickLabel = "Reproducir", pressedScale = 0.98f, onClick = onClick)
            .semantics { if (playing) stateDescription = "Sonando" }
            .highlight(edge, bg)
            .padding(start = 8.dp, end = 8.dp, top = verticalPadding, bottom = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Cover(song.coverColor, coverSize)
        Column(Modifier.weight(1f)) {
            SongTitle(song.title, color = titleColor)
            SubText(song.artist)
        }
        if (trailing != null) trailing() else Chakra(formatTime(song.durationSec), 12.sp, color = Fp.Mist, weight = FontWeight.Medium)
    }
}

/** Fila de playlist: dos carátulas apiladas, nombre y «42 CANCIONES». */
@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().fpClickable(onClickLabel = "Abrir playlist", pressedScale = 0.98f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StackedCover(playlist.coverColor, front = 40.dp, back = 38.dp, backX = 6.dp, frontY = 6.dp)
        Column(Modifier.weight(1f)) {
            SongTitle(playlist.name)
            Chakra(songCount(playlist.songIds.size), 11.sp, color = Fp.Mist, spacing = 1.sp)
        }
    }
}

/** Número de puesto (Chakra en negrita). */
@Composable
fun Position(n: Int, color: Color, size: TextUnit, width: Dp, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Chakra(n.toString(), size, modifier.width(width), color = color, weight = FontWeight.Bold, align = align)
}
