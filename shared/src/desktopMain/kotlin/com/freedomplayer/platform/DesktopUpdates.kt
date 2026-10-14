package com.freedomplayer.platform

import com.freedomplayer.jvm.UpdateInstaller
import java.io.File
import java.io.IOException

/**
 * Actualizarse en Windows: se baja el instalador .msi de la versión nueva, se abre y la app se
 * cierra para que el instalador pueda reemplazar sus archivos (el .msi lleva el mismo
 * «upgradeUuid» siempre, así que actualiza la instalación que haya). En otros sistemas no hay
 * instalador publicado: no se buscan versiones.
 */
class DesktopUpdateInstaller(
    private val windows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win"),
    private val launch: (List<String>) -> Unit = { ProcessBuilder(it).start() },
) : UpdateInstaller {
    /** Lo pone la ventana: cerrar la app (guardando la cola) una vez abierto el instalador. */
    @Volatile
    var onLaunched: () -> Unit = {}

    override val assetSuffix: String? = if (windows) "-windows.msi" else null

    override val downloadDir: File = File(System.getProperty("java.io.tmpdir"), "FreedomPlayer-actualizacion")

    override fun install(file: File): String? {
        if (!file.isFile) return "No se encuentra el instalador descargado. Toca REINTENTAR para bajarlo otra vez."
        return try {
            launch(listOf("msiexec", "/i", file.absolutePath))
            onLaunched()
            null
        } catch (e: IOException) {
            "No se pudo abrir el instalador de Windows (${e.message ?: e.javaClass.simpleName})."
        }
    }
}
