package com.freedomplayer.android.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.freedomplayer.jvm.UpdateInstaller
import java.io.File

/**
 * Actualizarse en Android: el APK de la versión nueva se baja a la caché de la app y se abre con
 * el instalador del sistema, que pide confirmación. Antes se comprueba lo que haría fallar la
 * instalación sin explicar nada: que esté firmado con otra clave o que falte el permiso de
 * «Instalar apps desconocidas».
 */
class AndroidUpdateInstaller(context: Context) : UpdateInstaller {
    private val app = context.applicationContext

    override val assetSuffix: String = "-android.apk"

    /** En la caché: la comparte el FileProvider (res/xml/update_paths.xml) y no va en las copias de seguridad. */
    override val downloadDir: File = File(app.cacheDir, "updates")

    override fun install(file: File): String? {
        if (!file.isFile) return "No se encuentra el APK descargado. Toca REINTENTAR para bajarlo otra vez."
        signatureProblem(file)?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(settings) }
            return "Activa «Permitir de esta fuente» para FreedomPlayer y vuelve a tocar REINTENTAR."
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            app.startActivity(install)
            null
        } catch (e: ActivityNotFoundException) {
            "Este móvil no tiene instalador de apps disponible."
        } catch (e: SecurityException) {
            "Android no deja abrir el instalador (${e.message ?: "permiso denegado"})."
        }
    }

    /**
     * Android solo instala una versión encima de otra si las dos llevan la misma firma. Si no
     * coinciden, se dice claro en vez de dejar que el instalador falle con «App no instalada».
     */
    private fun signatureProblem(file: File): String? {
        val pm = app.packageManager
        val archive = archiveInfo(pm, file)
        if (archive == null) {
            file.delete()
            return "El archivo descargado no es un APK válido. Toca REINTENTAR para bajarlo otra vez."
        }
        if (archive.packageName != app.packageName) return "El APK descargado no es de FreedomPlayer."
        val installed = runCatching { installedInfo(pm) }.getOrNull() ?: return null
        val newSigners = signers(archive)
        val oldSigners = signers(installed)
        // Si Android no ha podido leer las firmas, se deja decidir al instalador del sistema.
        if (newSigners.isEmpty() || oldSigners.isEmpty()) return null
        if (newSigners.intersect(oldSigners).isNotEmpty()) return null
        return "La versión nueva está firmada con otra clave y Android no deja instalarla encima. " +
            "Para pasar a ella hay que desinstalar FreedomPlayer antes (se borra lo guardado en el móvil)."
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
        }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        }

    /** Las firmas de un paquete (con su historial de rotación de clave, si lo tiene). */
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val s = info.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else {
            info.signatures
        }
        return list.orEmpty().map { it.toCharsString() }.toSet()
    }
}
