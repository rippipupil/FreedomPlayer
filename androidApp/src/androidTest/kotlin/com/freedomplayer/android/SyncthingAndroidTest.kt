package com.freedomplayer.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freedomplayer.core.SyncIds
import com.freedomplayer.jvm.SyncthingBackend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Syncthing de verdad en Android: el que va en el APK como biblioteca nativa (libsyncthing.so; en
 * x86_64, el emulador, compilado para Android del código oficial) se puede ejecutar, arranca solo
 * en 127.0.0.1 con la clave que se genera aquí, responde por su API y acepta la vinculación con el
 * PC (sus dos carpetas, dentro de la app).
 */
@RunWith(AndroidJUnit4::class)
class SyncthingAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Un id de dispositivo válido (con sus letras de control): el del ejemplo de la documentación de Syncthing. */
    private val pc = "P56IOI7-MZJNU2Y-IQGDREY-DM2MGTI-MGL3BXN-PQ6W5BM-TBBZ4TJ-XZWICQ2"

    @Test
    fun theBundledSyncthingStartsAndPairsWithThePc() {
        val exe = File(context.applicationInfo.nativeLibraryDir, "libsyncthing.so")
        assertTrue("Falta $exe en el APK", exe.isFile)
        val root = File(context.filesDir, "pruebas-syncthing").apply {
            deleteRecursively()
            mkdirs()
        }
        val backend = SyncthingBackend(
            executable = { exe },
            home = File(root, "syncthing"),
            music = File(root, "musica").apply { mkdirs() },
            library = File(root, "sincronizar"),
            showsQr = false,
            extraEnvironment = mapOf("HOME" to root.absolutePath),
        )
        try {
            runBlocking {
                withTimeout(90_000) {
                    val id = backend.start()
                    assertEquals(id, SyncIds.device(id))
                    assertTrue("El id de este móvil no puede ser el del PC", id != pc)
                    // Arrancarlo otra vez con él en marcha no lanza otro: es el mismo.
                    assertEquals(id, backend.start())
                    backend.pair(pc, "PC")
                    assertNull("Sin el PC conectado todavía no hay «12 de 20»", backend.progress(pc))
                    assertNull(backend.pendingDevice())
                }
            }
        } catch (e: Throwable) {
            val log = File(root, "syncthing/syncthing.log").takeIf { it.isFile }?.readText().orEmpty()
            throw AssertionError("Syncthing en Android: $e\n${crashSummary(log)}", e)
        } finally {
            backend.stop()
            root.deleteRecursively()
        }
    }

    /**
     * Lo que explica en el log de Syncthing por qué se ha cerrado: el principio y, si Go se ha
     * caído, la señal o el pánico con la goroutine que lo ha provocado (si no, el final del log).
     */
    private fun crashSummary(log: String): String {
        val lines = log.lines()
        val crash = lines.indexOfFirst { line ->
            line.startsWith("panic:") || line.startsWith("fatal error:") || Regex("^SIG[A-Z]+:").containsMatchIn(line)
        }
        val rest = if (crash >= 0) lines.drop(crash).take(30) else lines.drop(15).takeLast(25)
        return (lines.take(15) + listOf("…") + rest).joinToString("\n").take(6000)
    }
}
