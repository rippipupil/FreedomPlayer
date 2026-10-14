package com.freedomplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.SyncPhase
import com.freedomplayer.ui.components.Chakra
import com.freedomplayer.ui.components.HintNote
import com.freedomplayer.ui.components.Mood
import com.freedomplayer.ui.components.Nunito
import com.freedomplayer.ui.components.PrimaryButton
import com.freedomplayer.ui.components.SegmentBar
import com.freedomplayer.ui.components.StateCard
import com.freedomplayer.ui.components.screenPadding
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalWide

/**
 * Vincular el móvil y el PC (Syncthing). El PC enseña su QR (el id de su Syncthing); el móvil lo
 * escanea. Después, «Sincronizando con el PC 12 de 20» hasta que está todo.
 */
@Composable
fun SyncScreen(state: AppState, modifier: Modifier = Modifier) {
    val sync = state.sync
    val wide = LocalWide.current
    val other = if (wide) "el móvil" else "el PC"
    val pad = screenPadding()
    Column(modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        BackHeader(
            state,
            if (wide) "SYNCTHING · CON EL MÓVIL" else "SYNCTHING · CON EL PC",
            "Vincular",
            Modifier.padding(start = pad.calculateLeftPadding(LayoutDirection.Ltr), end = pad.calculateRightPadding(LayoutDirection.Ltr), bottom = 6.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = pad.calculateLeftPadding(LayoutDirection.Ltr), end = pad.calculateRightPadding(LayoutDirection.Ltr), top = 12.dp, bottom = pad.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (sync.phase) {
                SyncPhase.Off, SyncPhase.Starting -> StateCard(Mood.Contento, "Preparando la sincronización…", "Un momento: se está arrancando Syncthing.", Fp.Jade)
                SyncPhase.Waiting -> {
                    val qr = sync.qr
                    if (qr != null && qr.isNotEmpty()) {
                        HintNote("Abre FreedomPlayer en el móvil, ve a Ajustes › «Vincular con el PC» y escanea este código.", barColor = Fp.Jade)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode(qr) }
                        sync.myId?.let { Chakra(it, 10.sp, Modifier.align(Alignment.CenterHorizontally), color = Fp.Mist, spacing = 1.sp, maxLines = 2) }
                    } else {
                        HintNote("En el PC, abre FreedomPlayer y ve a Ajustes › «Vincular con el móvil»: saldrá un código QR.", barColor = Fp.Jade)
                        if (state.qrScanner != null) {
                            PrimaryButton("ESCANEAR EL QR DEL PC", { state.scanSyncCode() }, Modifier.fillMaxWidth(), fill = true)
                        } else {
                            Nunito("Aquí no se puede usar la cámara para leer el código.", 13.sp, color = Fp.Mist)
                        }
                    }
                }
                SyncPhase.Pairing -> StateCard(Mood.Owo, "Vinculando…", "Se está añadiendo $other y compartiendo la música.", Fp.Jade)
                SyncPhase.Syncing -> {
                    StateCard(Mood.Guino, "Sincronizando con $other", "${sync.done} de ${sync.total} canciones nuevas", Fp.Jade)
                    SegmentBar(10, if (sync.total > 0) sync.done.toFloat() / sync.total else 0f, Modifier.fillMaxWidth(), color = Fp.Jade, offColor = Fp.Jade.copy(alpha = 0.2f))
                }
                SyncPhase.Synced -> StateCard(Mood.Feliz, "Todo sincronizado", "Lo nuevo se pasa solo mientras los dos tengan FreedomPlayer abierto en la misma red.", Fp.Jade)
                SyncPhase.Failed -> {
                    StateCard(Mood.Triste, "No se pudo sincronizar", sync.error ?: "Algo ha fallado.", Fp.Neon)
                    PrimaryButton("REINTENTAR", { state.startSync() }, Modifier.fillMaxWidth(), fill = true)
                }
            }
            val error = sync.error
            if (error != null && sync.phase != SyncPhase.Failed) HintNote(error, barColor = Fp.Neon)
        }
    }
}

/** El QR en negro sobre blanco, con su margen blanco alrededor (para que la cámara lo lea). */
@Composable
private fun QrCode(cells: List<BooleanArray>, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.White).padding(14.dp)) {
        Canvas(Modifier.size(208.dp)) {
            val n = cells.size
            val cell = size.minDimension / n
            cells.forEachIndexed { y, row ->
                row.forEachIndexed { x, on ->
                    if (on) drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell, cell))
                }
            }
        }
    }
}
