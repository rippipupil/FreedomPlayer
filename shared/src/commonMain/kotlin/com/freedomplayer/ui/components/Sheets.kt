package com.freedomplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SampleData
import com.freedomplayer.core.formatTime
import com.freedomplayer.core.songCount
import com.freedomplayer.ui.AddTarget
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.Prompt
import com.freedomplayer.ui.RankTab
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.Sheet
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/** Lo que tapa a todo lo demás: el selector de «+», «⋯» y los nombres nuevos. */
@Composable
fun SheetHost(state: AppState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val reduce = LocalReduceMotion.current
    val overlay: Any? = state.prompt ?: state.sheet
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = overlay != null,
            enter = fadeIn(tween(Motion.STANDARD)),
            exit = fadeOut(tween(Motion.STANDARD)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Fp.Scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cerrar") {
                        state.back()
                    }
                    .semantics { contentDescription = "Cerrar" },
            )
        }
        AnimatedContent(
            targetState = overlay,
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (wide) Alignment.Center else Alignment.BottomCenter,
            transitionSpec = {
                val t: ContentTransform = when {
                    reduce -> fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                    wide -> (fadeIn(tween(Motion.STANDARD)) + scaleIn(tween(Motion.STANDARD, easing = Motion.Standard), initialScale = 0.96f)) togetherWith
                        (fadeOut(tween(Motion.QUICK)) + scaleOut(tween(Motion.QUICK), targetScale = 0.96f))
                    else -> slideInVertically(tween(Motion.SLOW, easing = Motion.Standard)) { it } togetherWith
                        slideOutVertically(tween(Motion.STANDARD, easing = Motion.Standard)) { it }
                }
                t
            },
            label = "sheet",
        ) { current ->
            when (current) {
                is Sheet.Add -> SheetSurface { AddSheet(state, current.target) }
                is Sheet.More -> SheetSurface { MoreSheet(state) }
                is Prompt -> SheetSurface { NamePrompt(state, current) }
                else -> Box(Modifier.size(0.dp))
            }
        }
    }
}

/** Hoja de abajo en el móvil (esquina cortada arriba a la izquierda) o tarjeta centrada en el PC. */
@Composable
private fun SheetSurface(content: @Composable ColumnScope.() -> Unit) {
    val wide = LocalWide.current
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = if (wide) Alignment.Center else Alignment.BottomCenter) {
        val shape = if (wide) cut(16.dp) else CutCornerShape(topStart = 18.dp)
        Column(
            Modifier
                .then(if (wide) Modifier.widthIn(max = 460.dp).fillMaxWidth() else Modifier.fillMaxWidth())
                .heightIn(max = maxHeight * 0.88f)
                .imePadding()
                .pointerInput(Unit) {
                    // La hoja se queda con los toques: tocar dentro no la cierra.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                    }
                }
                .background(Fp.Drawer, shape)
                .then(
                    if (wide) {
                        Modifier.border(1.dp, Fp.Neon.copy(alpha = 0.45f), shape)
                    } else {
                        Modifier.drawBehind {
                            drawRect(Fp.Neon.copy(alpha = 0.45f), size = Size(this.size.width, 1.dp.toPx()))
                        }
                    },
                )
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp + if (wide) 0.dp else nav),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetHeader(label: String, title: String) {
    Column {
        Chakra(label, 11.sp, color = Fp.Mist, spacing = 3.sp)
        Nunito(title, 18.sp, weight = FontWeight.ExtraBold, maxLines = 2)
    }
}

/** Una opción de la hoja: icono, texto y explicación. */
@Composable
private fun OptionRow(icon: FpIcon, title: String, detail: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .fpClickable(enabled = enabled, pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).background(Fp.Panel, cut(10.dp)).border(1.dp, Fp.Pink.copy(alpha = 0.5f), cut(10.dp)), contentAlignment = Alignment.Center) {
            FpIconView(icon, 18.dp, if (enabled) Fp.Pink else Fp.Pink.copy(alpha = 0.4f))
        }
        Column(Modifier.weight(1f)) {
            Nunito(title, 14.sp, weight = FontWeight.ExtraBold, color = if (enabled) Fp.Bone else Fp.Mist)
            if (detail != null) Nunito(detail, 12.sp, color = Fp.Mist, maxLines = 2)
        }
    }
}

/**
 * Selector de «+»: solo a la biblioteca o a una playlist (nueva o existente).
 * Los vídeos muestran el título ya limpio y se pueden corregir aquí mismo.
 */
@Composable
private fun ColumnScope.AddSheet(state: AppState, target: AddTarget) {
    val result = (target as? AddTarget.Result)?.result
    val isVideo = result?.kind == ResultKind.Video
    val clean = remember(target) { result?.let { state.songFor(it) } }
    var title by remember(target) { mutableStateOf(clean?.title ?: "") }
    var artist by remember(target) { mutableStateOf(clean?.artist ?: "") }
    val heading = when (target) {
        is AddTarget.Result -> if (isVideo) "AÑADIR VÍDEO COMO CANCIÓN" else "AÑADIR CANCIÓN"
        is AddTarget.Album -> "AÑADIR ÁLBUM ENTERO"
        is AddTarget.LibrarySong -> "AÑADIR A UNA PLAYLIST"
    }
    val name = when (target) {
        is AddTarget.Result -> clean?.title ?: target.result.title
        is AddTarget.Album -> target.album.title
        is AddTarget.LibrarySong -> state.song(target.songId)?.title ?: ""
    }
    SheetHeader(heading, name)
    if (isVideo && result != null) {
        HintNote("Título de YouTube: «${result.title}». Lo hemos limpiado; puedes cambiarlo ahora o más adelante.")
        UnderlineField(title, { title = it }, "Título", "Título de la canción", leadingIcon = FpIcons.Pencil)
        UnderlineField(artist, { artist = it }, "Artista", "Artista", leadingIcon = FpIcons.Pencil)
    }
    if (target is AddTarget.Album) {
        val ids = target.album.songIds
        val have = ids.count { id -> SampleData.catalog.firstOrNull { it.id == id }?.let { r -> state.librarySongFor(r) } != null }
        SubText("${songCount(ids.size).lowercase()} · $have ya en tu biblioteca")
    }
    if (result != null && !isVideo) SubText("${result.artist} · ${result.album ?: "Sencillo"} · ${formatTime(result.durationSec)}")
    val songId = (target as? AddTarget.LibrarySong)?.songId
    if (target !is AddTarget.LibrarySong) {
        OptionRow(FpIcons.Download, "Solo a la biblioteca", "Se descarga con carátula y letra.") {
            state.confirmAdd(target, null, title, artist)
        }
    }
    Chakra("A UNA PLAYLIST", 11.sp, color = Fp.Pink, spacing = 3.sp)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.playlists, key = { it.id }) { p ->
            val already = songId != null && songId in p.songIds
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaylistRow(
                    p,
                    onClick = { if (!already) state.confirmAdd(target, p.id, title, artist) },
                    modifier = Modifier.weight(1f),
                )
                if (already) InLibraryBadge(text = "YA ESTÁ")
            }
        }
    }
    OptionRow(FpIcons.Plus, "Nueva playlist…") {
        state.sheet = null
        state.prompt = Prompt.NewPlaylist(then = target, title = title, artist = artist)
    }
    SecondaryButton("CANCELAR", { state.sheet = null }, Modifier.fillMaxWidth(), color = Fp.Mist, lineColor = Fp.Pink.copy(alpha = 0.25f))
}

/** «⋯» del reproductor. */
@Composable
private fun ColumnScope.MoreSheet(state: AppState) {
    val song = state.currentSong
    SheetHeader("MÁS OPCIONES", song?.title ?: "Nada sonando")
    if (song != null) {
        OptionRow(FpIcons.Plus, "Añadir a una playlist") { state.sheet = Sheet.Add(AddTarget.LibrarySong(song.id)) }
        OptionRow(FpIcons.Ranking, "Cambiar la nota", "En Ranking, tocando sus estrellas.") {
            state.go(Screen.Rank)
            state.rankTab = RankTab.General
            state.editingScoreId = song.id
        }
    }
    OptionRow(FpIcons.Pencil, "Editar datos de la canción", "Llega en una próxima versión.") { state.soon("Editar los datos") }
    OptionRow(FpIcons.Queue, "Ver la cola", "Llega en una próxima versión.") { state.soon("La cola") }
    SecondaryButton("CERRAR", { state.sheet = null }, Modifier.fillMaxWidth(), color = Fp.Mist, lineColor = Fp.Pink.copy(alpha = 0.25f))
}

/** Nombre de una playlist o un ranking nuevo. */
@Composable
private fun ColumnScope.NamePrompt(state: AppState, prompt: Prompt) {
    var name by remember(prompt) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(prompt) {
        runCatching { focus.requestFocus() }
    }
    val playlist = prompt is Prompt.NewPlaylist
    SheetHeader(if (playlist) "NUEVA PLAYLIST" else "NUEVO RANKING", "Ponle nombre")
    UnderlineField(
        value = name,
        onValueChange = { name = it },
        placeholder = if (playlist) "Noches de lluvia" else "Mejores del verano",
        label = "Nombre",
        modifier = Modifier.focusRequester(focus),
        leadingIcon = FpIcons.Pencil,
        imeAction = ImeAction.Done,
        onSubmit = { state.submitPrompt(name) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("CANCELAR", { state.prompt = null }, Modifier.weight(1f), color = Fp.Mist, lineColor = Fp.Pink.copy(alpha = 0.25f))
        PrimaryButton("CREAR", { state.submitPrompt(name) }, Modifier.weight(1f), fill = true, enabled = name.isNotBlank())
    }
}
