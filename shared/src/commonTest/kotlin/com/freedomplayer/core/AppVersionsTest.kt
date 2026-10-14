package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionsTest {
    @Test
    fun newerReleasesAreDetected() {
        assertTrue(AppVersions.isNewer("0.5.0", "0.4.0"))
        assertTrue(AppVersions.isNewer("0.5.0", "0.4.31"))
        assertTrue(AppVersions.isNewer("1.0.0", "0.9.99"))
        assertTrue(AppVersions.isNewer("v0.4.1", "0.4.0"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(AppVersions.isNewer("0.4.0", "0.4.0"))
        // Una compilación de prueba (0.4.31) va por delante de la 0.4.0 publicada.
        assertFalse(AppVersions.isNewer("0.4.0", "0.4.31"))
        assertFalse(AppVersions.isNewer("0.3.9", "0.4.0"))
    }

    @Test
    fun betasGoBeforeTheFinalVersion() {
        assertTrue(AppVersions.isNewer("1.0.0", "1.0.0-beta.1"))
        assertFalse(AppVersions.isNewer("1.0.0-beta.1", "1.0.0"))
        assertTrue(AppVersions.isNewer("1.0.0-beta.2", "1.0.0-beta.1"))
    }

    @Test
    fun unknownVersionsNeverAsk() {
        assertFalse(AppVersions.isNewer("0.5.0", "dev"))
        assertFalse(AppVersions.isNewer("algo", "0.4.0"))
        assertNull(AppVersions.compare("0.5", "0.4.0"))
        assertEquals(0, AppVersions.compare("v1.2.3", "1.2.3"))
    }

    @Test
    fun assetIsFoundBySuffix() {
        val r = AppRelease(
            "0.5.0", "", "https://example/r",
            listOf(
                ReleaseAsset("FreedomPlayer-0.5.0-android.apk", "https://example/a.apk", 10),
                ReleaseAsset("FreedomPlayer-0.5.0-windows.msi", "https://example/w.msi", 20),
            ),
        )
        assertEquals("https://example/a.apk", r.asset("-android.apk")?.url)
        assertEquals("https://example/w.msi", r.asset("-windows.msi")?.url)
        assertNull(r.asset("-mac.dmg"))
    }
}
