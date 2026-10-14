package com.freedomplayer.android

import android.app.Application
import androidx.compose.runtime.snapshotFlow
import com.freedomplayer.android.playback.AndroidEngine
import com.freedomplayer.android.update.AndroidUpdateInstaller
import com.freedomplayer.platform.AndroidLibrary
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.needsForeground
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
        AppState(System.currentTimeMillis(), AndroidLibrary.services(this, engine, AndroidUpdateInstaller(this))).also { s ->
            s.appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "dev"
            scope.launch { s.run() }
            // Syncthing sigue mientras se sincroniza, aunque se cierre la pantalla: en primer plano
            // solo mientras hace falta (arrancar, vincular, pasar canciones).
            scope.launch {
                snapshotFlow { s.sync.phase.needsForeground }.collect { needed ->
                    SyncService.update(this@FreedomApp, needed)
                }
            }
        }
    }
}
