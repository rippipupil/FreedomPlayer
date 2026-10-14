package com.freedomplayer.android

import android.database.ContentObserver
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.session.MediaController
import com.freedomplayer.android.playback.PlaybackService
import com.freedomplayer.platform.AndroidLibrary
import com.freedomplayer.ui.App
import com.freedomplayer.ui.AppState
import com.freedomplayer.ui.components.Mood
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** El estado es del proceso ([FreedomApp]): girar la pantalla o cambiar el tema no lo pierde. */
    private val app: FreedomApp get() = application as FreedomApp
    private val state: AppState get() = app.state
    private val main = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var playback: ListenableFuture<MediaController>? = null

    /** «Añadir tus archivos de audio»: el selector de archivos del sistema, solo audio. */
    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        val s = state
        s.toast(if (uris.size == 1) "Añadiendo el archivo…" else "Añadiendo ${uris.size} archivos…", Mood.Owo)
        // En el ámbito de la app: si se sale de la pantalla a mitad, la copia sigue.
        app.scope.launch {
            val (songs, failed) = AudioImporter(applicationContext, AndroidLibrary.dirs(applicationContext)).import(uris)
            s.addImported(songs, failed)
        }
    }

    private val openPicker: () -> Unit = { pickAudio.launch(arrayOf("audio/*")) }

    /** «Quitar animaciones» de Android pone la escala de animaciones a 0: entonces se reduce el movimiento. */
    private val animationObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = readReduceMotion()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema siempre oscuro: iconos claros en las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "dev"
        setContent {
            // Atrás cierra lo que esté abierto (hojas, paneles, reproductor…) antes de salir de la app.
            BackHandler(enabled = state.canGoBack) { state.back() }
            App(state = state, wide = false, version = version, runLoop = false)
        }
        state.fileImporter = openPicker
    }

    override fun onDestroy() {
        if (state.fileImporter === openPicker) state.fileImporter = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        // Mientras se ve la app, conectada al servicio de reproducción (notificación, bloqueo…).
        playback = PlaybackService.connect(this)
        readReduceMotion()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animationObserver,
        )
        watchNetwork()
    }

    override fun onStop() {
        PlaybackService.disconnect(playback)
        playback = null
        contentResolver.unregisterContentObserver(animationObserver)
        networkCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        super.onStop()
    }

    private fun readReduceMotion() {
        val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        state.reduceMotion = scale == 0f
    }

    /** «Sin conexión» sale arriba mientras no haya una red con internet. */
    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        fun hasInternet(caps: NetworkCapabilities?): Boolean =
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        state.systemOnline = hasInternet(cm.getNetworkCapabilities(cm.activeNetwork))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val online = hasInternet(caps)
                main.post { state.systemOnline = online }
            }

            override fun onAvailable(network: Network) {
                main.post { state.systemOnline = true }
            }

            override fun onLost(network: Network) {
                main.post { state.systemOnline = hasInternet(cm.getNetworkCapabilities(cm.activeNetwork)) }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }
}
