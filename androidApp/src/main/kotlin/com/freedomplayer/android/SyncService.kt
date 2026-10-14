package com.freedomplayer.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Syncthing va en un proceso hijo de la app: este servicio en primer plano («sincronización de
 * datos», con su notificación) hace que Android no cierre la app mientras se sincroniza.
 *
 * En Android 15 o más, ese tipo de servicio puede ir 6 horas al día con la app en segundo plano
 * (vuelve a contar al abrirla). Al acabarse, Android llama a [onTimeout]: se para el servicio y la
 * sincronización, y la app lo avisa. No se vuelve a poner en primer plano solo: hasta que se abra
 * la app, Android no lo deja (ForegroundServiceStartNotAllowedException).
 */
class SyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Sincronizar con el PC", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("FreedomPlayer")
            .setContentText("Sincronizando con el PC")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: RuntimeException) {
            // Android no lo deja en primer plano: en Android 15 o más, porque ya se han gastado las
            // 6 horas del día («Time limit already exhausted for foreground service type
            // dataSync»). Sin cerrar la app: se para y se avisa.
            Log.w(TAG, "Android no deja sincronizar en primer plano", e)
            stopSelf()
            stoppedBySystem()
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15 o más: se han acabado las 6 horas del día del servicio de sincronización. Hay que
     * pararlo en unos segundos (si no, Android cierra la app con un error).
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.i(TAG, "Android ha agotado el tiempo de la sincronización en primer plano")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        stoppedBySystem()
    }

    /** La app para Syncthing y lo avisa; no lo vuelve a arrancar sola. */
    private fun stoppedBySystem() {
        runCatching { (application as? FreedomApp)?.state?.syncStoppedBySystem() }
            .onFailure { Log.w(TAG, "No se ha podido parar la sincronización", it) }
    }

    companion object {
        private const val TAG = "FreedomPlayer"
        private const val CHANNEL = "sincronizar"
        private const val NOTIFICATION_ID = 7

        /** Mientras haga falta (ver SyncPhase.needsForeground), en primer plano; si no, parado. */
        fun update(context: Context, active: Boolean) {
            val intent = Intent(context, SyncService::class.java)
            if (active) {
                // Desde segundo plano Android puede no dejar arrancarlo: se sigue sin él.
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
