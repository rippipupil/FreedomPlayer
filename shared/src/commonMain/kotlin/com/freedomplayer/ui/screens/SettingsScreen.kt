package com.freedomplayer.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.LocalAppVersion
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.FpIcon
import com.freedomplayer.ui.components.FpIconView
import com.freedomplayer.ui.components.FpIcons
import com.freedomplayer.ui.components.Ghost
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.ScreenHeader
import com.freedomplayer.ui.components.SectionHeader
import com.freedomplayer.ui.components.SwitchRow
import com.freedomplayer.ui.components.fpClickable
import com.freedomplayer.ui.components.fpFloat
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/**
 * Ajustes (sencilla): reproducción, descargas, el PC, sincronización, pruebas de estados
 * y «Acerca de». Lo que aún no existe avisa de que llega en una próxima versión.
 */
@Composable
fun SettingsScreen(state: AppState, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    val settings = state.settings
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
            SwitchRow("Volumen igualado", "Todas las canciones suenan igual de fuerte.", settings.normalizeVolume, { settings.normalizeVolume = it })
            SwitchRow("Fundido entre canciones", "Una canción entra mientras la otra se apaga (crossfade).", settings.crossfade, { settings.crossfade = it })
            SwitchRow("Letra sincronizada", "Resalta la línea que suena. Si lo apagas, la letra se ve entera.", settings.syncedLyrics, { settings.syncedLyrics = it })
            ActionRow(FpIcons.Equalizer, "Ecualizador", "Llega en una próxima versión.") { state.soon("El ecualizador") }

            SectionHeader(next(), "DESCARGAS", Modifier.padding(top = 18.dp))
            SwitchRow("Guardar para escuchar sin conexión", "Audio, carátula y letra se quedan en el dispositivo.", settings.keepOffline, { settings.keepOffline = it })
            ActionRow(FpIcons.Download, "Carpeta y calidad", "Se guarda el mejor audio que da YouTube. Elegir carpeta llega en una próxima versión.") {
                state.soon("Elegir carpeta y calidad")
            }
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
            ActionRow(FpIcons.Link, if (wide) "Vincular con el móvil" else "Vincular con el PC", "Con un código QR. Llega en una próxima versión.") {
                state.soon("Vincular con un QR")
            }

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

            SectionHeader(next(), "ACERCA DE", Modifier.padding(top = 18.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
