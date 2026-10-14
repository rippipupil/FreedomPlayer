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
 * Syncthing de verdad en Android: el binario oficial que va en el APK como biblioteca nativa
 * (libsyncthing.so) se puede ejecutar, arranca solo en 127.0.0.1 con la clave que se genera aquí,
 * responde por su API y acepta la vinculación con el PC (sus dos carpetas, dentro de la app).
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
            val log = File(root, "syncthing/syncthing.log").takeIf { it.isFile }?.readText().orEmpty().takeLast(4000)
            throw AssertionError("Syncthing en Android: $e\n$log", e)
        } finally {
            backend.stop()
            root.deleteRecursively()
        }
    }
}
