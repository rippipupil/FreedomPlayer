package com.freedomplayer.desktop

import java.net.NetworkInterface
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Lo que el PC cuenta del sistema: versión, red y si se pide reducir el movimiento. */
object DesktopSystem {

    /** La versión del instalador (jpackage la deja en esta propiedad); «dev» al ejecutar con Gradle. */
    fun appVersion(): String = System.getProperty("jpackage.app-version")?.takeIf { it.isNotBlank() } ?: "dev"

    /**
     * ¿Hay alguna conexión de red activa (que no sea la interna del equipo)? No comprueba que
     * haya internet de verdad: sirve para enseñar «Sin conexión» si se cae el wifi o el cable.
     */
    fun isOnline(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { ni ->
            ni.isUp && !ni.isLoopback && !ni.isVirtual &&
                ni.inetAddresses.toList().any { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        }
    } catch (_: Exception) {
        true
    }

    /**
     * ¿Ha pedido el sistema reducir el movimiento? Windows: «Efectos de animación» desactivado
     * (SPI_GETCLIENTAREAANIMATION). macOS: «Reducir movimiento». Linux (GNOME): animaciones apagadas.
     * FP_REDUCE_MOTION=1 lo fuerza.
     */
    fun prefersReducedMotion(): Boolean {
        System.getenv("FP_REDUCE_MOTION")?.let { return it == "1" || it.equals("true", ignoreCase = true) }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return try {
            when {
                os.contains("win") -> run(powershell(WINDOWS_ANIMATIONS))?.trim()?.lines()?.lastOrNull()?.trim().equals("False", ignoreCase = true)
                os.contains("mac") -> run(listOf("defaults", "read", "com.apple.universalaccess", "reduceMotion"))?.trim() == "1"
                else -> run(listOf("gsettings", "get", "org.gnome.desktop.interface", "enable-animations"))?.trim() == "false"
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Devuelve True si Windows tiene las animaciones encendidas y False si no. */
    private const val WINDOWS_ANIMATIONS =
        "Add-Type -Namespace Fp -Name U -MemberDefinition '[DllImport(\"user32.dll\")] public static extern bool " +
            "SystemParametersInfo(uint a, uint b, ref bool c, uint d);'; " +
            "\$v = \$true; [void][Fp.U]::SystemParametersInfo(0x1042, 0, [ref]\$v, 0); \$v"

    /** PowerShell con el script en base64, para no pelearse con las comillas de la línea de órdenes. */
    private fun powershell(script: String): List<String> {
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
    }

    private fun run(command: List<String>): String? {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (!process.waitFor(6, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        return process.inputStream.bufferedReader().readText()
    }
}
