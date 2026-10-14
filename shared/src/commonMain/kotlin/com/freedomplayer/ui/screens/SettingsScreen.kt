package com.freedomplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.core.AudioQuality
import com.freedomplayer.core.Crossfade
import com.freedomplayer.core.EqPreset
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.LocalAppVersion
import com.freedomplayer.ui.Settings
import com.freedomplayer.ui.Sheet
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.ChoiceChip
import com.freedomplayer.ui.components.FpIcon
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Motion
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.ScreenHeader
import com.freedomplayer.ui.components.SectionHeader
import com.freedomplayer.ui.components.SwitchRow
import com.freedomplayer.ui.components.TextAction
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.fpTween
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/** Toques en «Acerca de» que enseñan «PROBAR ESTADOS». */
private const val TEST_TOOLS_TAPS = 5

/**
 * Ajustes (sencilla): reproducción, descargas, el PC, sincronización, pruebas de estados
 * y «Acerca de». Lo que aún no existe avisa de que llega en una próxima versión.
 */
@Composable
fun SettingsScreen(state: AppState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val settings = state.settings
    var aboutTaps by remember { mutableStateOf(0) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding()),
    ) {
        Column(Modifier.then(if (wide) Modifier.widthIn(max = 720.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ScreenHeader("AJUSTES", "Ajustes", Modifier.padding(bottom = 14.dp))

            var n = 0
            fun next(): String = (++n).toString().padStart(2, '0')

            SectionHeader(next(), "REPRODUCCIÓN")
            SwitchRow(
                "Volumen igualado",
                "Todas las canciones igual de fuertes: se mide cada una (una vez) y se bajan las que suenan más.",
                settings.normalizeVolume,
                { settings.normalizeVolume = it },
            )
            SwitchRow("Fundido entre canciones", "La siguiente entra mientras la que suena se va apagando.", settings.crossfade, { settings.crossfade = it })
            AnimatedVisibility(
                visible = settings.crossfade,
                enter = fadeIn(fpTween()) + expandVertically(fpTween()),
                exit = fadeOut(fpTween()) + shrinkVertically(fpTween()),
            ) {
                CrossfadeLength(settings)
            }
            SwitchRow("Letra sincronizada", "Resalta la línea que suena. Si lo apagas, la letra se ve entera.", settings.syncedLyrics, { settings.syncedLyrics = it })
            ActionRow(FpIcons.Equalizer, "Ecualizador", EqPreset.matching(settings.eqGains).label + " · 5 bandas") { state.sheet = Sheet.Equalizer }

            SectionHeader(next(), "DESCARGAS", Modifier.padding(top = 18.dp))
            SwitchRow("Guardar para escuchar sin conexión", "Audio, carátula y letra se quedan en el dispositivo.", settings.keepOffline, { settings.keepOffline = it })
            QualityChoice(settings)
            DownloadFolder(state)
            if (state.canImportFiles) {
                ActionRow(FpIcons.Plus, "Añadir tus archivos de audio", "MP3, M4A, FLAC, OGG… Se copian a la biblioteca con su carátula.") {
                    state.importFiles()
                }
            }

            if (wide) {
                SectionHeader(next(), "PC", Modifier.padding(top = 18.dp))
                SwitchRow("Cerrar a la bandeja", "Al cerrar la ventana, la música sigue sonando en la bandeja.", settings.closeToTray, { settings.closeToTray = it })
                SwitchRow("Mini-reproductor siempre encima", "La ventanita flotante se queda por encima de las demás.", settings.miniOnTop, { settings.miniOnTop = it })
            }

            SectionHeader(next(), "SINCRONIZACIÓN", Modifier.padding(top = 18.dp))
            ActionRow(FpIcons.Link, if (wide) "Vincular con el móvil" else "Vincular con el PC", if (state.canSync) "Con un código QR, con Syncthing." else "Con un código QR. Llega en una próxima versión.") {
                state.openSync()
            }

            // Herramientas para probar los avisos: solo en el modo de ejemplo o tras tocar 5 veces
            // «Acerca de» (como las opciones de desarrollador de Android). No se guardan.
            if (state.isDemo || aboutTaps >= TEST_TOOLS_TAPS) {
                SectionHeader(next(), "PROBAR ESTADOS", Modifier.padding(top = 18.dp))
                SwitchRow(
                    "Simular sin conexión",
                    "Enseña el aviso «Sin conexión» y pausa buscar y descargar, como si no hubiera red.",
                    settings.demoOffline,
                    { settings.demoOffline = it },
                )
                SwitchRow(
                    "Simular biblioteca vacía",
                    "Enseña las pantallas como el primer día, sin canciones.",
                    settings.demoEmpty,
                    { state.setDemoEmpty(it) },
                )
            }

            SectionHeader(next(), "ACERCA DE", Modifier.padding(top = 18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .fpClickable(onClickLabel = "Acerca de FreedomPlayer", role = null, pressedScale = 0.99f) { aboutTaps++ }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Ghost(Mood.Feliz, 44.dp, Modifier.fpFloat())
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Chakra("FREEDOMPLAYER ${LocalAppVersion.current}", 14.sp, color = Fp.Pink, spacing = 2.sp)
                    Nunito(
                        if (state.isDemo) {
                            "Modo de ejemplo: datos inventados, sin buscar ni descargar de verdad."
                        } else {
                            "Busca en YouTube Music y YouTube con NewPipe Extractor, descarga el mejor audio y las letras de LRCLIB."
                        },
                        12.sp,
                        color = Fp.Mist,
                        lineHeight = 17.sp,
                    )
                    Nunito("Tipografías Chakra Petch y Nunito (SIL Open Font License).", 12.sp, color = Fp.MistDim, lineHeight = 17.sp)
                }
            }
        }
    }
}

/** Fila de ajuste que abre algo: icono, título y explicación. */
@Composable
private fun ActionRow(icon: FpIcon, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .fpClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Nunito(title, 14.sp, weight = FontWeight.ExtraBold)
            Nunito(description, 12.sp, color = Fp.Mist, lineHeight = 17.sp)
        }
        FpIconView(icon, 18.dp, Fp.Idle)
    }
}

/** Cuánto dura el fundido entre canciones. */
@Composable
private fun CrossfadeLength(settings: Settings) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chakra("DURACIÓN", 11.sp, Modifier.padding(end = 4.dp), color = Fp.Mist, spacing = 1.5.sp)
        for (seconds in Crossfade.choices) {
            ChoiceChip("$seconds s", settings.crossfadeSeconds == seconds) { settings.crossfadeSeconds = seconds }
        }
    }
}

/** Calidad del audio que se descarga: tres opciones y qué significa la elegida. */
@Composable
private fun QualityChoice(settings: Settings) {
    val enter = fadeIn(fpTween())
    val exit = fadeOut(fpTween(Motion.QUICK))
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Nunito("Calidad del audio", 14.sp, weight = FontWeight.ExtraBold)
        AnimatedContent(
            targetState = settings.downloadQuality,
            transitionSpec = { (enter togetherWith exit) using SizeTransform(clip = false) },
            label = "downloadQuality",
        ) { quality ->
            Nunito(quality.detail, 12.sp, color = Fp.Mist, lineHeight = 17.sp)
        }
        Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (quality in AudioQuality.entries) {
                ChoiceChip(quality.label, settings.downloadQuality == quality) { settings.downloadQuality = quality }
            }
        }
    }
}

/**
 * Dónde se guardan las descargas. En el PC se elige con el selector de carpetas del sistema (y se
 * puede volver a la de la app); en el móvil se queda dentro de la app.
 */
@Composable
private fun DownloadFolder(state: AppState) {
    if (!state.canChooseFolder) {
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
            Nunito("Carpeta de descargas", 14.sp, weight = FontWeight.ExtraBold)
            Nunito(
                if (LocalWide.current) {
                    "La carpeta de la app."
                } else {
                    "Dentro de la app. En el móvil no se cambia: así la sincronización con el PC encuentra siempre la música."
                },
                12.sp,
                color = Fp.Mist,
                lineHeight = 17.sp,
            )
        }
        return
    }
    val chosen = state.settings.downloadFolder
    val enter = fadeIn(fpTween())
    val exit = fadeOut(fpTween(Motion.QUICK))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .fpClickable(onClickLabel = "Elegir la carpeta de descargas", pressedScale = 0.98f, onClick = { state.chooseDownloadFolder() })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Nunito("Carpeta de descargas", 14.sp, weight = FontWeight.ExtraBold)
            AnimatedContent(
                targetState = chosen ?: state.appMusicFolder ?: "La carpeta de la app",
                transitionSpec = { (enter togetherWith exit) using SizeTransform(clip = false) },
                label = "downloadFolder",
            ) { path ->
                Nunito(path, 12.sp, color = Fp.Bone, lineHeight = 17.sp, maxLines = 2)
            }
            Nunito("Solo cambia dónde van las descargas nuevas: lo que ya tienes se queda donde está.", 12.sp, color = Fp.Mist, lineHeight = 17.sp)
        }
        FpIconView(FpIcons.Folder, 18.dp, Fp.Idle)
    }
    AnimatedVisibility(
        visible = chosen != null,
        enter = fadeIn(fpTween()) + expandVertically(fpTween()),
        exit = fadeOut(fpTween()) + shrinkVertically(fpTween()),
    ) {
        TextAction("VOLVER A LA CARPETA DE LA APP", { state.setDownloadFolder(null) })
    }
}
