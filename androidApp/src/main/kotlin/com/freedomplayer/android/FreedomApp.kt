package com.freedomplayer.android

import android.app.Application
import androidx.compose.runtime.snapshotFlow
import com.freedomplayer.android.playback.AndroidEngine
import com.freedomplayer.platform.AndroidLibrary
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.SyncPhase
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * La app vive aquí mientras viva el proceso, no en la pantalla: girar el móvil no pierde nada, y
 * con la pantalla apagada siguen la música, las descargas y el contador de escuchas.
 */
class FreedomApp : Application() {
    /** Para lo que no debe pararse al cerrar la pantalla (el bucle de la app, importar archivos). */
    val scope = MainScope()

    val state: AppState by lazy {
        val engine = AndroidEngine(this)
        AppState(System.currentTimeMillis(), AndroidLibrary.services(this, engine)).also { s ->
            scope.launch { s.run() }
            // Syncthing sigue mientras se sincroniza, aunque se cierre la pantalla.
            scope.launch {
                snapshotFlow { s.sync.phase }.collect { phase ->
                    SyncService.update(this@FreedomApp, phase != SyncPhase.Off && phase != SyncPhase.Failed)
                }
            }
        }
    }
}
