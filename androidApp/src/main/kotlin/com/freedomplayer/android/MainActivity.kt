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
import com.freedomplayer.ui.App
import com.freedomplayer.ui.AppState

/** El estado vive mientras viva el proceso: girar la pantalla o cambiar el tema no lo pierde. */
private object StateHolder {
    val state: AppState by lazy { AppState() }
}

class MainActivity : ComponentActivity() {
    private val state: AppState get() = StateHolder.state
    private val main = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
            App(state = state, wide = false, version = version)
        }
    }

    override fun onStart() {
        super.onStart()
        readReduceMotion()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animationObserver,
        )
        watchNetwork()
    }

    override fun onStop() {
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
