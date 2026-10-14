package com.freedomplayer.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freedomplayer.android.playback.ThemedArtwork
import com.freedomplayer.android.playback.ThemedBitmapLoader
import com.freedomplayer.android.update.AndroidUpdateInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** La imagen con el tema que ve el sistema (notificación y bloqueo) y abrir una actualización. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ThemeAndUpdateTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isDark(pixel: Int) = Color.red(pixel) < 60 && Color.green(pixel) < 60 && Color.blue(pixel) < 80

    @Test
    fun artworkPutsTheCoverOnTheNightTheme() {
        val cover = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0, 200, 0)) }
        val art = ThemedArtwork.compose(cover)
        assertEquals(ThemedArtwork.SIZE, art.width)
        assertEquals(ThemedArtwork.SIZE, art.height)
        assertTrue("las esquinas son la noche del tema", isDark(art.getPixel(4, ThemedArtwork.SIZE / 2)))
        val middle = art.getPixel(ThemedArtwork.SIZE / 2, (ThemedArtwork.SIZE * 0.45f).toInt())
        assertTrue("en medio, la carátula", Color.green(middle) > 150 && Color.red(middle) < 80)
    }

    @Test
    fun songsWithoutCoverStillGetTheThemedImage() {
        val loader = ThemedBitmapLoader(DataSourceBitmapLoader(context))
        val art = loader.loadBitmap(ThemedArtwork.PLACEHOLDER).get(10, TimeUnit.SECONDS)
        assertEquals(ThemedArtwork.SIZE, art.width)
        assertFalse(isDark(art.getPixel(ThemedArtwork.SIZE / 2, (ThemedArtwork.SIZE * 0.45f).toInt())))
    }

    @Test
    fun unreadableCoverFallsBackToTheTheme() {
        val loader = ThemedBitmapLoader(DataSourceBitmapLoader(context))
        val missing = android.net.Uri.fromFile(File(context.cacheDir, "no-existe.jpg"))
        val art = loader.loadBitmap(missing).get(10, TimeUnit.SECONDS)
        assertEquals(ThemedArtwork.SIZE, art.width)
    }

    @Test
    fun aBrokenDownloadIsNotOpenedAndIsDeleted() {
        val installer = AndroidUpdateInstaller(context)
        val broken = File(installer.downloadDir.apply { mkdirs() }, "FreedomPlayer-9.9.9-android.apk").apply { writeText("no es un apk") }
        val problem = installer.install(broken)
        assertNotNull(problem)
        assertTrue(problem!!.contains("no es un APK válido"))
        assertFalse(broken.exists())
    }
}
