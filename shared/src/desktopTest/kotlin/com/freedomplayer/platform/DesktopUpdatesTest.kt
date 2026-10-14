package com.freedomplayer.platform

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Abrir el instalador de Windows y cerrar la app (sin abrir nada de verdad). */
class DesktopUpdatesTest {
    @Test
    fun windowsOpensTheMsiAndClosesTheApp() {
        val launched = mutableListOf<List<String>>()
        var closed = false
        val installer = DesktopUpdateInstaller(windows = true, launch = { launched += it }).apply { onLaunched = { closed = true } }
        val msi = Files.createTempFile("FreedomPlayer-0.5.0-windows", ".msi").toFile()
        try {
            assertEquals("-windows.msi", installer.assetSuffix)
            assertNull(installer.install(msi))
            assertEquals(listOf("msiexec", "/i", msi.absolutePath), launched.single())
            assertTrue(closed)
        } finally {
            msi.delete()
        }
    }

    @Test
    fun otherSystemsDoNotLookForUpdates() {
        assertNull(DesktopUpdateInstaller(windows = false).assetSuffix)
    }

    @Test
    fun problemsAreExplainedAndTheAppStaysOpen() {
        var closed = false
        val installer = DesktopUpdateInstaller(windows = true, launch = { throw IOException("sin msiexec") }).apply { onLaunched = { closed = true } }
        val msi = Files.createTempFile("FreedomPlayer", ".msi").toFile()
        try {
            assertNotNull(installer.install(msi))
            assertNotNull(installer.install(File(msi.parentFile, "no-existe.msi")))
            assertTrue(!closed)
        } finally {
            msi.delete()
        }
    }
}
