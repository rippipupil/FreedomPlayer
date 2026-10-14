package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.AlbumResult
import com.freedomplayer.core.PlaylistResult
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.formatTime
import com.freedomplayer.core.songCount
import com.freedomplayer.ui.AddTarget
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.explain
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.TabResults
import com.freedomplayer.ui.Screen
import com.freedomplayer.ui.SearchTab
import com.freedomplayer.ui.Side
import com.freedomplayer.ui.components.AddButton
import com.freedomplayer.ui.components.AddState
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.CoverImage
import com.freedomplayer.ui.components.EmptyState
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.FpTabs
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.InLibraryBadge
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.ScreenHeader
import com.freedomplayer.ui.components.SearchBox
import com.freedomplayer.ui.components.SecondaryButton
import com.freedomplayer.ui.components.SegmentBar
import com.freedomplayer.ui.components.StackedCover
import com.freedomplayer.ui.components.SubText
import com.freedomplayer.ui.components.fpBob
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.itemMotion
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/**
 * Buscar (YouTube Music y YouTube): canciones, álbumes, vídeos y listas de otra gente. La carátula
 * (o la miniatura) es la vista previa; «+» abre el selector de playlist. Pegar el enlace de una
 * lista de YouTube la abre. En el móvil el contenido va a 46 dp de los bordes para que la pestaña
 * derecha no pise los «+».
 */
@Composable
fun SearchScreen(state: AppState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val pad = screenPadding()
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier
            .fillMaxSize()
            .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()),
    ) {
        val side = Modifier.padding(horizontal = if (wide) 0.dp else 46.dp)
        Column(
            Modifier.then(if (wide) Modifier.padding(pad.sidesOnly()) else side),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader("YOUTUBE MUSIC · SIN LÍMITE", "Buscar")
            SearchBox(
                value = state.query,
                onValueChange = { state.query = it },
                enabled = state.online,
                onSubmit = { keyboard?.hide() },
            )
            // Cuatro pestañas: en el móvil no caben y se desplazan de lado.
            FpTabs(
                labels = listOf("CANCIONES", "ÁLBUMES", "VÍDEOS", "LISTAS"),
                selected = state.searchTab.ordinal,
                onSelect = { state.searchTab = SearchTab.entries[it] },
                modifier = if (wide) Modifier else Modifier.horizontalScroll(rememberScrollState()),
                tabPadding = if (wide) 14.dp else 10.dp,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val link = state.linkedList
            if (!state.online) {
                OfflineSearch(state, Modifier.then(if (wide) Modifier.padding(pad.sidesOnly()) else side).padding(top = 16.dp))
            } else if (link != null) {
                LinkedList(state, link, Modifier.then(if (wide) Modifier.padding(pad.sidesOnly()) else side).padding(top = 16.dp))
            } else {
                val reduce = LocalReduceMotion.current
                AnimatedContent(
                    targetState = state.searchTab,
                    transitionSpec = {
                        if (reduce) {
                            fadeIn(tween(Motion.QUICK)) togetherWith fadeOut(tween(Motion.QUICK))
                        } else {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn(tween(Motion.STANDARD)) + slideInHorizontally(tween(Motion.STANDARD, easing = Motion.Standard)) { dir * it / 10 }) togetherWith
                                (fadeOut(tween(Motion.QUICK)) + slideOutHorizontally(tween(Motion.QUICK)) { -dir * it / 10 })
                        }
                    },
                    label = "searchTab",
                ) { tab ->
                    val listPad = if (wide) pad.sidesOnly(top = 16.dp, bottom = 12.dp) else PaddingValues(start = 46.dp, end = 46.dp, top = 16.dp, bottom = 12.dp)
                    when (tab) {
                        SearchTab.Songs -> SongResults(state, listPad)
                        SearchTab.Albums -> AlbumResults(state, listPad)
                        SearchTab.Videos -> VideoResults(state, listPad)
                        SearchTab.Playlists -> PlaylistResults(state, listPad)
                    }
                }
            }
        }
        SpotifyLink(
            onClick = { state.go(Screen.SpotifyImport) },
            modifier = if (wide) Modifier.padding(pad.sidesOnly()) else side,
        )
    }
}

/** Solo los lados de un relleno (y lo que se quiera arriba y abajo). */
private fun PaddingValues.sidesOnly(top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues =
    PaddingValues(
        start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top = top,
        bottom = bottom,
    )

/**
 * Qué enseña una pestaña: buscando (filas fantasma), el error con «REINTENTAR», «Nada por aquí»
 * o los resultados. Mientras llega una búsqueda nueva se siguen viendo los anteriores.
 */
@Composable
private fun <T> TabContent(
    state: AppState,
    tab: SearchTab,
    results: TabResults<T>,
    pad: PaddingValues,
    skeleton: @Composable () -> Unit,
    content: @Composable (List<T>) -> Unit,
) {
    val q = state.query.trim()
    val error = results.error
    val fresh = results.query == q
    Crossfade(
        targetState = when {
            q.isEmpty() -> 0
            error != null && fresh -> 1
            results.items.isNotEmpty() -> 2
            results.loading || !fresh -> 3
            else -> 0
        },
        animationSpec = tween(Motion.STANDARD),
        label = "searchState",
    ) { mode ->
        when (mode) {
            1 -> SearchFailed(state, error ?: FailureKind.Unknown, pad)
            2 -> content(results.items)
            3 -> Box(Modifier.padding(pad)) { skeleton() }
            else -> NoResults(state, tab, pad)
        }
    }
}

@Composable
private fun SongResults(state: AppState, pad: PaddingValues) {
    TabContent(state, SearchTab.Songs, state.songResults, pad, skeleton = { SkeletonRows(48.dp, 48.dp, gap = 14.dp) }) { results ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(results, key = { it.id }) { r ->
                ResultRow(state, r, itemMotion())
            }
        }
    }
}

@Composable
private fun AlbumResults(state: AppState, pad: PaddingValues) {
    TabContent(state, SearchTab.Albums, state.albumResults, pad, skeleton = { SkeletonRows(76.dp, 76.dp, gap = 24.dp, rows = 4) }) { albums ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(albums, key = { it.key }) { album ->
                AlbumRow(state, album, itemMotion())
            }
        }
    }
}

@Composable
private fun VideoResults(state: AppState, pad: PaddingValues) {
    TabContent(state, SearchTab.Videos, state.videoResults, pad, skeleton = { SkeletonRows(96.dp, 54.dp, gap = 16.dp, rows = 5) }) { videos ->
        VideoList(state, videos, pad)
    }
}

@Composable
private fun PlaylistResults(state: AppState, pad: PaddingValues) {
    TabContent(state, SearchTab.Playlists, state.playlistResults, pad, skeleton = { SkeletonRows(76.dp, 76.dp, gap = 24.dp, rows = 4) }) { lists ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "note") {
                HintNote("Listas que ha hecho otra gente. Ábrela y añade solo las canciones que quieras.", barColor = Fp.Violet)
            }
            items(lists, key = { it.key }) { list ->
                OtherListRow(state, list, itemMotion())
            }
        }
    }
}

/** Fila de una lista de otra persona: carátulas apiladas, nombre, «QUIÉN · N CANCIONES» y «VER CANCIONES ›». */
@Composable
private fun OtherListRow(state: AppState, list: PlaylistResult, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .fpClickable(onClickLabel = "Ver las canciones de ${list.title}", pressedScale = 0.98f) { state.openOtherList(list.url, list) },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StackedCover(list.coverColor, front = 76.dp, back = 74.dp, backX = 8.dp, frontY = 9.dp, image = list.cover)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Nunito(list.title, 15.sp, weight = FontWeight.ExtraBold, maxLines = 2)
            Chakra(
                listOfNotNull(list.owner.uppercase().ifBlank { null }, list.count?.let { songCount(it) } ?: "LISTA").joinToString(" · "),
                11.sp,
                color = Fp.Mist,
                spacing = 1.sp,
                weight = FontWeight.Medium,
            )
            Chakra("VER CANCIONES ›", 11.sp, color = Fp.Pink, spacing = 1.5.sp)
        }
    }
}

/** Lo escrito es el enlace de una lista de YouTube: en vez de buscarlo, se ofrece abrirla. */
@Composable
private fun LinkedList(state: AppState, url: String, modifier: Modifier) {
    EmptyState(
        title = "Enlace de una lista",
        body = if ("music.youtube.com" in url) {
            "Es una lista de YouTube Music. Ábrela para escuchar sus canciones y añadir las que quieras."
        } else {
            "Es una lista de YouTube. Ábrela para escuchar sus vídeos y añadir los que quieras como canciones."
        },
        modifier = modifier,
        ghosts = { Ghost(Mood.Owo, 52.dp, Modifier.fpBob()) },
    ) {
        PrimaryButton("ABRIR LISTA", { state.openOtherList(url) }, Modifier.width(220.dp), fill = true, icon = FpIcons.Link)
        SecondaryButton("BORRAR", { state.query = "" }, Modifier.width(220.dp))
    }
}

@Composable
private fun VideoList(state: AppState, videos: List<SearchResult>, pad: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "note") {
            HintNote("YouTube normal: directos, remixes y covers que no están en YouTube Music.", barColor = Fp.Violet)
        }
        items(videos, key = { it.id }) { v ->
            VideoRow(state, v, itemMotion())
        }
    }
}

/** El «+» de un resultado: añadir, descargando (con su %) o ✓. */
internal fun AppState.addStateFor(r: SearchResult): AddState {
    val fraction = downloadFraction(r.id)
    return when {
        fraction != null -> AddState.Downloading(fraction)
        r.id in addedIds && librarySongFor(r) != null -> AddState.Done
        else -> AddState.Idle
    }
}

/** Fila de canción: vista previa en la carátula, título, «Artista · Álbum · 3:05» y «+» o EN BIBLIO. */
@Composable
internal fun ResultRow(state: AppState, r: SearchResult, modifier: Modifier = Modifier) {
    val previewing = state.preview?.id == r.id
    val inLibrary = state.librarySongFor(r) != null && r.id !in state.addedIds
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PreviewCover(
            color = r.coverColor,
            image = r.cover,
            width = 48.dp,
            height = 48.dp,
            previewing = previewing,
            playing = previewing && state.previewPlaying,
            enabled = state.online,
            onClick = { state.togglePreview(r) },
        )
        Column(
            Modifier
                .weight(1f)
                .fpClickable(
                    onClickLabel = if (previewing) "Parar vista previa" else "Escuchar vista previa",
                    pressedScale = 0.98f,
                    enabled = state.online,
                ) { state.togglePreview(r) },
        ) {
            Nunito(r.title, 14.sp, color = Fp.Bone, weight = FontWeight.ExtraBold, maxLines = 1)
            SubText(listOfNotNull(r.artist.ifBlank { null }, r.album, formatTime(r.durationSec)).joinToString(" · "))
        }
        if (inLibrary) {
            InLibraryBadge()
        } else {
            AddButton(state.addStateFor(r), "Añadir a la biblioteca", onClick = { state.requestAdd(AddTarget.Result(r)) })
        }
    }
}

/**
 * Carátula o miniatura que hace de botón de vista previa: ▶ en reposo; mientras suena, tres
 * barras que bailan y un aro rosa alrededor.
 */
@Composable
private fun PreviewCover(
    color: Long,
    image: String?,
    width: Dp,
    height: Dp,
    previewing: Boolean,
    playing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val ring by animateFloatAsState(if (previewing) 1f else 0f, fpTween(), label = "previewRing")
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(width, height)
            .drawBehind {
                if (ring > 0f) {
                    val gap = 3.dp.toPx()
                    drawRect(
                        Fp.Neon.copy(alpha = ring),
                        topLeft = Offset(-gap, -gap),
                        size = Size(this.size.width + gap * 2, this.size.height + gap * 2),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            .fpClickable(onClickLabel = null, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (previewing) "Parar vista previa" else "Escuchar vista previa" }
            .clip(shape)
            .background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            CoverImage(image, Modifier.fillMaxSize())
            // Un velo para que el ▶ se vea sobre cualquier imagen.
            Box(Modifier.fillMaxSize().background(Color(0x5908050E)))
        }
        content()
        Crossfade(targetState = previewing, animationSpec = tween(Motion.QUICK), label = "previewIcon") { on ->
            if (on) {
                EqBars(animate = playing)
            } else {
                FpIconView(FpIcons.Play, 14.dp, Fp.Bone.copy(alpha = if (enabled) 0.85f else 0.4f))
            }
        }
    }
}

/** Las tres barras rosas que bailan mientras suena la vista previa (quietas si se reduce el movimiento). */
@Composable
private fun EqBars(animate: Boolean) {
    val moving = animate && !LocalReduceMotion.current
    if (moving) {
        val transition = rememberInfiniteTransition(label = "eq")
        val bars = (0 until 3).map { i ->
            transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = Motion.EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 200),
                ),
                label = "eqBar$i",
            )
        }
        EqCanvas { i -> bars[i].value }
    } else {
        EqCanvas { i -> StillBars[i] }
    }
}

private val StillBars = listOf(0.6f, 1f, 0.45f)

/** Dibuja las barras; la altura se lee al dibujar, así que bailar no recompone nada. */
@Composable
private fun EqCanvas(level: (Int) -> Float) {
    Canvas(Modifier.size(15.dp, 18.dp)) {
        val bar = 3.dp.toPx()
        val gap = 3.dp.toPx()
        for (i in 0 until 3) {
            val h = this.size.height * level(i)
            drawRect(Fp.Neon, topLeft = Offset(i * (bar + gap), this.size.height - h), size = Size(bar, h))
        }
    }
}

/**
 * Fila de álbum: carátulas apiladas, título, «ARTISTA · AÑO · N CANCIONES» y «+ ÁLBUM ENTERO».
 * Tocar la carátula o el nombre abre el álbum para ver sus canciones una a una.
 */
@Composable
private fun AlbumRow(state: AppState, album: AlbumResult, modifier: Modifier = Modifier) {
    LaunchedEffect(album.key) { state.loadAlbumTracks(album) }
    val results = state.albumTracks[album.key]
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        StackedCover(
            album.coverColor,
            front = 76.dp,
            back = 74.dp,
            backX = 8.dp,
            frontY = 9.dp,
            image = album.cover,
            modifier = Modifier.fpClickable(onClickLabel = "Ver las canciones de ${album.title}", pressedScale = 0.97f) { state.openAlbum(album) },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                Modifier.fpClickable(onClickLabel = "Ver las canciones de ${album.title}", pressedScale = 0.98f) { state.openAlbum(album) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Nunito(album.title, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                Chakra(
                    listOfNotNull(album.artist.uppercase().ifBlank { null }, album.year, results?.let { songCount(it.size) } ?: "ÁLBUM").joinToString(" · ") + "  ›",
                    11.sp,
                    color = Fp.Mist,
                    spacing = 1.sp,
                    weight = FontWeight.Medium,
                )
            }
            AlbumAction(state, album)
        }
    }
}

/** «+ ÁLBUM ENTERO», cómo va su descarga o «ÁLBUM EN TU BIBLIO» (en Buscar y dentro del álbum). */
@Composable
internal fun AlbumAction(state: AppState, album: AlbumResult) {
    val results = state.albumTracks[album.key]
    val have = results?.count { state.librarySongFor(it) != null } ?: 0
    val job = state.albumJob(album)
    Crossfade(
        targetState = when {
            job != null -> 1
            results != null && have == results.size && results.isNotEmpty() -> 2
            else -> 0
        },
        animationSpec = tween(Motion.STANDARD),
        label = "albumAction",
    ) { mode ->
        when (mode) {
            1 -> {
                val j = state.albumJob(album)
                Column(Modifier.heightIn(min = 44.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    Chakra(
                        when {
                            j == null -> "LISTO"
                            j.resolving -> "BUSCANDO CANCIONES…"
                            else -> "DESCARGANDO ${minOf(j.done + 1, j.total)} DE ${j.total}"
                        },
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
                InLibraryBadge(text = "ÁLBUM EN TU BIBLIO")
            }
            else -> PrimaryButton(
                "+ ÁLBUM ENTERO",
                { state.requestAdd(AddTarget.Album(album)) },
                height = 36.dp,
                cutSize = 9.dp,
                fontSize = 11.sp,
                spacing = 1.5.sp,
                horizontalPadding = 14.dp,
            )
        }
    }
}

/** Fila de vídeo: miniatura 16:9 con la duración (también es vista previa), título en dos líneas y «+». */
@Composable
private fun VideoRow(state: AppState, v: SearchResult, modifier: Modifier = Modifier) {
    val previewing = state.preview?.id == v.id
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PreviewCover(
            color = v.coverColor,
            image = v.cover,
            width = 96.dp,
            height = 54.dp,
            previewing = previewing,
            playing = previewing && state.previewPlaying,
            enabled = state.online,
            onClick = { state.togglePreview(v) },
        ) {
            Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.BottomEnd) {
                Box(Modifier.background(Color(0xD908050E)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Chakra(formatTime(v.durationSec), 10.sp)
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fpClickable(
                    onClickLabel = if (previewing) "Parar vista previa" else "Escuchar vista previa",
                    pressedScale = 0.98f,
                    enabled = state.online,
                ) { state.togglePreview(v) },
        ) {
            Nunito(v.title, 13.sp, color = Fp.Bone, weight = FontWeight.ExtraBold, maxLines = 2, lineHeight = 17.sp)
            SubText(v.note ?: v.artist)
        }
        AddButton(state.addStateFor(v), "Añadir vídeo como canción", onClick = { state.requestAdd(AddTarget.Result(v)) })
    }
}

/** «Nada por aquí»: llena el hueco y propone otra pestaña o borrar la búsqueda. */
@Composable
private fun NoResults(state: AppState, tab: SearchTab, pad: PaddingValues) {
    val q = state.query.trim()
    val (title, body) = when {
        q.isEmpty() -> "Busca algo" to
            "Escribe una canción, un artista o un álbum, o pega el enlace de una lista de YouTube. Los vídeos traen directos, remixes y covers."
        tab == SearchTab.Songs -> "Nada por aquí" to "Ninguna canción coincide con «$q». Prueba en la pestaña Vídeos."
        tab == SearchTab.Albums -> "Nada por aquí" to "Ningún álbum coincide con «$q». Prueba con el nombre del artista."
        tab == SearchTab.Playlists -> "Nada por aquí" to "Ninguna lista coincide con «$q». También puedes pegar aquí el enlace de una lista de YouTube."
        else -> "Nada por aquí" to "Ningún vídeo coincide con «$q»."
    }
    EmptyState(
        title = title,
        body = body,
        modifier = Modifier.padding(pad),
        ghosts = { Ghost(if (q.isEmpty()) Mood.Owo else Mood.Sorpresa, 52.dp, Modifier.fpBob()) },
    ) {
        if (q.isNotEmpty()) {
            if (tab != SearchTab.Videos) {
                PrimaryButton("PROBAR EN VÍDEOS", { state.searchTab = SearchTab.Videos }, Modifier.width(220.dp), fill = true)
            }
            SecondaryButton("BORRAR BÚSQUEDA", { state.query = "" }, Modifier.width(220.dp))
        }
    }
}

/** Sin conexión: el buscador no funciona, pero la biblioteca sí. */
@Composable
private fun OfflineSearch(state: AppState, modifier: Modifier) {
    EmptyState(
        title = "Sin conexión",
        body = "Tu biblioteca sigue aquí. Buscar y descargar vuelve cuando haya internet.",
        modifier = modifier.padding(bottom = 12.dp),
        ghosts = { Ghost(Mood.Triste, 52.dp, Modifier.fpBob()) },
    ) {
        PrimaryButton("VER MI BIBLIOTECA", { state.openPanel(Side.Left) }, Modifier.width(220.dp), fill = true)
    }
}

/** «PEGAR PLAYLIST DE SPOTIFY»: texto jade entre dos líneas discontinuas. */
@Composable
private fun SpotifyLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .fpClickable(pressedScale = 0.98f, role = Role.Button, onClick = onClick)
            .drawBehind {
                val px = 1.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                val c = Fp.Jade.copy(alpha = 0.6f)
                drawLine(c, Offset(0f, px / 2), Offset(this.size.width, px / 2), strokeWidth = px, pathEffect = dash)
                drawLine(c, Offset(0f, this.size.height - px / 2), Offset(this.size.width, this.size.height - px / 2), strokeWidth = px, pathEffect = dash)
            }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FpIconView(FpIcons.Link, 18.dp, Fp.JadeLight)
        Chakra("PEGAR PLAYLIST DE SPOTIFY", 12.sp, color = Fp.JadeLight, spacing = 1.5.sp)
    }
}

/**
 * Mientras se busca: filas fantasma (carátula y dos rayas) que laten despacio. Quietas si se
 * reduce el movimiento.
 */
@Composable
internal fun SkeletonRows(coverWidth: Dp, coverHeight: Dp, gap: Dp, rows: Int = 7) {
    val reduce = LocalReduceMotion.current
    val pulse = if (reduce) {
        null
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.45f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900, easing = Motion.EaseInOut), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = pulse?.value ?: 0.7f }
            .semantics { contentDescription = "Buscando" },
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Chakra("BUSCANDO…", 11.sp, color = Fp.Mist, spacing = 3.sp)
        repeat(rows) { i ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(coverWidth, coverHeight).clip(RoundedCornerShape(3.dp)).background(Fp.Panel))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.7f else 0.55f).height(11.dp).background(Fp.Panel))
                    Box(Modifier.fillMaxWidth(if (i % 3 == 0) 0.45f else 0.35f).height(9.dp).background(Fp.Panel.copy(alpha = 0.7f)))
                }
            }
        }
    }
}

/** La búsqueda ha fallado: qué ha pasado y «REINTENTAR». */
@Composable
private fun SearchFailed(state: AppState, kind: FailureKind, pad: PaddingValues) {
    EmptyState(
        title = "No se pudo buscar",
        body = kind.explain(),
        modifier = Modifier.padding(pad),
        ghosts = { Ghost(Mood.Triste, 52.dp, Modifier.fpBob()) },
    ) {
        PrimaryButton("REINTENTAR", { state.retrySearch() }, Modifier.width(220.dp), fill = true)
    }
}
