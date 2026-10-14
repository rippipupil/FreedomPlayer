package com.freedomplayer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.freedomplayer.platform.DesktopAudioImporter
import com.freedomplayer.platform.ImageFiles
import com.freedomplayer.platform.DesktopEngine
import com.freedomplayer.platform.DesktopLibrary
import com.freedomplayer.platform.DesktopUpdateInstaller
import com.freedomplayer.platform.FolderPicker
import com.freedomplayer.platform.MediaKey
import com.freedomplayer.platform.MediaKeys
import com.freedomplayer.ui.App
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.FpIcon
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.GhostPainter
import com.freedomplayer.ui.components.LocalReduceMotion
import com.freedomplayer.ui.components.MiniPlayer
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.nowPlaying
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.FreedomTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.io.File
import javax.swing.SwingUtilities

/**
 * FreedomPlayer en el PC: ventana sin marco del sistema con la barra de título del diseño,
 * bordes para cambiar el tamaño, icono en la bandeja (sigue sonando al cerrar) y
 * mini-reproductor flotante.
 */
fun main() = application {
    val updater = remember { DesktopUpdateInstaller() }
    val state = remember {
        AppState(System.currentTimeMillis(), DesktopLibrary.services(DesktopEngine(), updater))
            .also { it.appVersion = DesktopSystem.appVersion() }
    }
    // Abierto el instalador de la versión nueva, la app se cierra para que pueda reemplazarla.
    updater.onLaunched = { SwingUtilities.invokeLater { exitApplication() } }
    var mainVisible by remember { mutableStateOf(true) }
    var miniOpen by remember { mutableStateOf(false) }
    var trayNoticeShown by remember { mutableStateOf(false) }
    val screen = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        position = WindowPosition(Alignment.Center),
        size = DpSize(minOf(1280, screen.width - 40).dp, minOf(800, screen.height - 40).dp),
    )
    val trayState = rememberTrayState()
    val tray = isTraySupported
    // El icono de la ventana y de la bandeja es el fantasma que se lleva puesto.
    val skin = state.settings.ghostSkin
    val icon = remember(skin) { GhostPainter(Mood.Feliz, skin = skin) }

    // Red y «reducir movimiento» del sistema.
    LaunchedEffect(state) {
        state.reduceMotion = withContext(Dispatchers.IO) { DesktopSystem.prefersReducedMotion() }
        while (true) {
            state.systemOnline = withContext(Dispatchers.IO) { DesktopSystem.isOnline() }
            delay(4_000)
        }
    }

    // Teclas multimedia del teclado (Windows), aunque la ventana no tenga el foco o esté en la bandeja.
    DisposableEffect(state) {
        val keys = MediaKeys.listen { key -> SwingUtilities.invokeLater { onMediaKey(state, key) } }
        onDispose { keys?.close() }
    }

    // «+ TUS ARCHIVOS DE AUDIO»: el selector de archivos del sistema; se copian a la biblioteca.
    val scope = rememberCoroutineScope()
    DisposableEffect(state) {
        val importer = DesktopAudioImporter(DesktopLibrary.dirs())
        val open: () -> Unit = {
            val files = chooseAudioFiles()
            if (files.isNotEmpty()) {
                state.toast(if (files.size == 1) "Añadiendo el archivo…" else "Añadiendo ${files.size} archivos…", Mood.Owo)
                scope.launch {
                    val (songs, failed) = importer.import(files)
                    state.addImported(songs, failed)
                }
            }
        }
        state.fileImporter = open
        onDispose { if (state.fileImporter === open) state.fileImporter = null }
    }

    // Wrapped → «GUARDAR IMAGEN»: la tarjeta, en PNG, en Imágenes › FreedomPlayer.
    DisposableEffect(state) {
        val save: (ImageBitmap, String) -> String? = { image, name ->
            val file = ImageFiles.savePng(image, ImageFiles.picturesDir(), name)
            "Imágenes › FreedomPlayer › ${file.name}"
        }
        state.imageSharer = save
        onDispose { if (state.imageSharer === save) state.imageSharer = null }
    }

    // Ajustes → «Carpeta de descargas»: el selector de carpetas de Windows. Lo ya descargado no se mueve.
    DisposableEffect(state) {
        val appFolder = DesktopLibrary.dirs().music.absolutePath
        state.appMusicFolder = appFolder
        var picking = false
        val pick: () -> Unit = {
            if (!picking) {
                picking = true
                scope.launch {
                    try {
                        val start = File(state.settings.downloadFolder ?: appFolder)
                        val chosen = withContext(Dispatchers.IO) { FolderPicker.choose("Carpeta para las descargas", start) }
                        if (chosen != null) state.setDownloadFolder(chosen.absolutePath.takeIf { it != appFolder })
                    } finally {
                        picking = false
                    }
                }
            }
        }
        state.folderPicker = pick
        onDispose { if (state.folderPicker === pick) state.folderPicker = null }
    }

    fun toTray() {
        if (!tray) {
            exitApplication()
            return
        }
        mainVisible = false
        if (!trayNoticeShown) {
            trayNoticeShown = true
            trayState.sendNotification(
                Notification("FreedomPlayer sigue sonando", "Está en la bandeja del sistema. Haz clic en el fantasma para volver.", Notification.Type.Info),
            )
        }
    }

    if (tray) {
        Tray(
            icon = icon,
            state = trayState,
            tooltip = "FreedomPlayer",
            onAction = {
                mainVisible = true
                miniOpen = false
            },
            menu = {
                Item(if (state.isPlaying) "Pausar" else "Reproducir", onClick = { state.togglePlay() })
                Item("Siguiente", onClick = { state.next() })
                Item("Anterior", onClick = { state.previous() })
                Separator()
                Item("Abrir FreedomPlayer", onClick = {
                    mainVisible = true
                    miniOpen = false
                })
                Item("Mini-reproductor", onClick = {
                    miniOpen = true
                    mainVisible = false
                })
                Separator()
                Item("Salir", onClick = ::exitApplication)
            },
        )
    }

    Window(
        onCloseRequest = { if (state.settings.closeToTray) toTray() else exitApplication() },
        state = windowState,
        visible = mainVisible,
        title = "FreedomPlayer",
        icon = icon,
        undecorated = true,
        resizable = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) state.back() else false
        },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(960, 640)
        }
        val maximized = windowState.placement == WindowPlacement.Maximized
        fun toggleMaximized() {
            if (maximized) {
                windowState.placement = WindowPlacement.Floating
            } else {
                // Sin marco del sistema, maximizar taparía la barra de tareas: se limita al área útil.
                window.maximizedBounds = usableBounds(window.graphicsConfiguration)
                windowState.placement = WindowPlacement.Maximized
            }
        }
        Box(Modifier.fillMaxSize()) {
            App(
                state = state,
                wide = true,
                version = DesktopSystem.appVersion(),
                titleBar = {
                    TitleBar(
                        maximized = maximized,
                        onMini = {
                            miniOpen = true
                            mainVisible = false
                        },
                        onMinimize = {
                            if (tray) {
                                toTray()
                            } else {
                                windowState.isMinimized = true
                            }
                        },
                        onToggleMaximized = { toggleMaximized() },
                        onClose = { if (state.settings.closeToTray) toTray() else exitApplication() },
                    )
                },
            )
            if (!maximized) ResizeHandles()
        }
    }

    if (miniOpen) {
        val miniState = rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(
                (screen.x + screen.width - 420).dp,
                (screen.y + screen.height - 190).dp,
            ),
            size = DpSize(392.dp, 152.dp),
        )
        Window(
            onCloseRequest = { miniOpen = false },
            state = miniState,
            title = "FreedomPlayer · mini",
            icon = icon,
            undecorated = true,
            resizable = false,
            alwaysOnTop = state.settings.miniOnTop,
        ) {
            MiniWindow(
                state = state,
                onExpand = {
                    mainVisible = true
                    miniOpen = false
                },
                onClose = {
                    miniOpen = false
                    if (!mainVisible && !tray) mainVisible = true
                },
            )
        }
    }
}

/** Lo que hace cada tecla multimedia del teclado. */
private fun onMediaKey(state: AppState, key: MediaKey) {
    when (key) {
        MediaKey.PlayPause -> state.togglePlay()
        MediaKey.Next -> state.next()
        MediaKey.Previous -> state.previous()
        MediaKey.Stop -> if (state.isPlaying) state.togglePlay()
    }
}

/** Formatos de audio que se pueden añadir (los que lee FFmpeg y se suelen tener). */
private val audioExtensions = listOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "webm", "aif", "aiff", "alac", "mka")

/** El selector de archivos del sistema, para elegir uno o varios archivos de audio. */
private fun chooseAudioFiles(): List<File> {
    val dialog = FileDialog(null as Frame?, "Añadir tus archivos de audio", FileDialog.LOAD)
    dialog.isMultipleMode = true
    // En Windows el filtro va en el nombre («*.mp3;*.m4a;…»); en Linux y macOS, con el filtro de nombres.
    dialog.file = audioExtensions.joinToString(";") { "*.$it" }
    dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in audioExtensions }
    dialog.isVisible = true
    return dialog.files.orEmpty().filter { it.isFile }
}

/** El área útil de la pantalla donde está la ventana (sin la barra de tareas). */
private fun usableBounds(gc: java.awt.GraphicsConfiguration?): Rectangle {
    if (gc == null) return GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
    val b = gc.bounds
    return Rectangle(insets.left, insets.top, b.width - insets.left - insets.right, b.height - insets.top - insets.bottom)
}

/**
 * Barra de título del diseño (38 dp): «✦ FREEDOMPLAYER» a la izquierda (se arrastra para mover la
 * ventana; doble clic maximiza) y cuatro botones: mini-reproductor flotante, minimizar a la bandeja,
 * maximizar y cerrar (sigue sonando en la bandeja).
 */
@Composable
private fun FrameWindowScope.TitleBar(
    maximized: Boolean,
    onMini: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximized: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Fp.Night.copy(alpha = 0.6f))
            .drawBehind {
                val px = 1.dp.toPx()
                drawRect(Fp.Pink.copy(alpha = 0.12f), topLeft = androidx.compose.ui.geometry.Offset(0f, this.size.height - px), size = Size(this.size.width, px))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDraggableArea(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleMaximized() }) },
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FpIconView(FpIcons.Sparkle, 12.dp, Fp.Neon)
                Chakra("FREEDOMPLAYER", 12.sp, color = Fp.Pink, spacing = 3.sp)
            }
        }
        TitleButton(FpIcons.MiniWindow, "Mini-reproductor flotante", 14.dp, onClick = onMini)
        TitleButton(FpIcons.Minimize, "Minimizar a la bandeja", 12.dp, onClick = onMinimize)
        TitleButton(
            if (maximized) FpIcons.Restore else FpIcons.Maximize,
            if (maximized) "Restaurar" else "Maximizar",
            12.dp,
            onClick = onToggleMaximized,
        )
        TitleButton(FpIcons.CloseWindow, "Cerrar (sigue sonando en la bandeja)", 12.dp, danger = true, onClick = onClose)
    }
}

/** Botón de la barra de título (46×38): se ilumina al pasar el ratón; el de cerrar, en neón. */
@Composable
private fun TitleButton(icon: FpIcon, label: String, iconSize: Dp, danger: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg = when {
        !hovered -> Color.Transparent
        danger -> Fp.Neon
        else -> Fp.Panel
    }
    Box(
        Modifier
            .size(46.dp, 38.dp)
            .hoverable(source)
            .background(bg)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        FpIconView(icon, iconSize, if (hovered && danger) Fp.Ink else if (hovered) Fp.Bone else Fp.Idle)
    }
}

/** Bordes de 5 dp para cambiar el tamaño de la ventana (sin marco del sistema no los hay). */
@Composable
private fun FrameWindowScope.ResizeHandles() {
    val edge = 5.dp
    Box(Modifier.fillMaxSize()) {
        ResizeEdge(Modifier.align(Alignment.CenterStart).width(edge).fillMaxHeight(), Cursor.W_RESIZE_CURSOR, left = true)
        ResizeEdge(Modifier.align(Alignment.CenterEnd).width(edge).fillMaxHeight(), Cursor.E_RESIZE_CURSOR, right = true)
        ResizeEdge(Modifier.align(Alignment.TopCenter).height(edge).fillMaxWidth(), Cursor.N_RESIZE_CURSOR, top = true)
        ResizeEdge(Modifier.align(Alignment.BottomCenter).height(edge).fillMaxWidth(), Cursor.S_RESIZE_CURSOR, bottom = true)
        ResizeEdge(Modifier.align(Alignment.TopStart).size(edge * 2), Cursor.NW_RESIZE_CURSOR, left = true, top = true)
        ResizeEdge(Modifier.align(Alignment.TopEnd).size(edge * 2), Cursor.NE_RESIZE_CURSOR, right = true, top = true)
        ResizeEdge(Modifier.align(Alignment.BottomStart).size(edge * 2), Cursor.SW_RESIZE_CURSOR, left = true, bottom = true)
        ResizeEdge(Modifier.align(Alignment.BottomEnd).size(edge * 2), Cursor.SE_RESIZE_CURSOR, right = true, bottom = true)
    }
}

@Composable
private fun FrameWindowScope.ResizeEdge(
    modifier: Modifier,
    cursor: Int,
    left: Boolean = false,
    right: Boolean = false,
    top: Boolean = false,
    bottom: Boolean = false,
) {
    val w = window
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                    val from = w.bounds
                    val min = w.minimumSize
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val p = MouseInfo.getPointerInfo()?.location ?: continue
                        val dx = p.x - start.x
                        val dy = p.y - start.y
                        var x = from.x
                        var y = from.y
                        var width = from.width
                        var height = from.height
                        if (right) width = maxOf(min.width, from.width + dx)
                        if (bottom) height = maxOf(min.height, from.height + dy)
                        if (left) {
                            width = maxOf(min.width, from.width - dx)
                            x = from.x + from.width - width
                        }
                        if (top) {
                            height = maxOf(min.height, from.height - dy)
                            y = from.y + from.height - height
                        }
                        w.setBounds(x, y, width, height)
                    }
                }
            },
    )
}

/**
 * Mini-reproductor flotante: una ventanita siempre encima con el mini-reproductor del móvil.
 * Se mueve arrastrando la franja de arriba; la carátula o ⤢ vuelven a la ventana grande.
 */
@Composable
private fun FrameWindowScope.MiniWindow(state: AppState, onExpand: () -> Unit, onClose: () -> Unit) {
    FreedomTheme(wide = false) {
        CompositionLocalProvider(LocalReduceMotion provides state.reduceMotion) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Fp.Night)
                    .border(1.dp, Fp.Pink.copy(alpha = 0.35f)),
            ) {
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            Modifier.fillMaxSize().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FpIconView(FpIcons.Sparkle, 10.dp, Fp.Neon)
                            Chakra("FREEDOMPLAYER", 10.sp, color = Fp.Pink, spacing = 2.sp)
                        }
                    }
                    MiniTitleButton(FpIcons.Maximize, "Abrir la ventana grande", onExpand)
                    MiniTitleButton(FpIcons.CloseWindow, "Cerrar el mini-reproductor", onClose)
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    if (state.nowPlaying() != null) {
                        MiniPlayer(state, Modifier.padding(horizontal = 6.dp), onOpen = onExpand)
                    } else {
                        Nunito(
                            "Nada sonando. Elige una canción en la ventana grande.",
                            13.sp,
                            Modifier.align(Alignment.Center).padding(16.dp),
                            color = Fp.Mist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTitleButton(icon: FpIcon, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(32.dp, 28.dp)
            .hoverable(source)
            .background(if (hovered) Fp.Panel else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        FpIconView(icon, 10.dp, if (hovered) Fp.Bone else Fp.Idle)
    }
}
