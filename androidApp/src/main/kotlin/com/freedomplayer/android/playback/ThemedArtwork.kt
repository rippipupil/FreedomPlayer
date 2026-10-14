package com.freedomplayer.android.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * La imagen que ven la notificación, el panel de reproducción y la pantalla de bloqueo. En
 * Android 13+ el sistema pinta los mandos él mismo y saca los colores de esa imagen, así que en
 * vez de la carátula sola se le da la carátula montada en el tema: el callejón de noche, la
 * farola rosa, el marco de neón y un fantasmita. Así la tarjeta del sistema queda con los
 * colores de FreedomPlayer y no con los de cada portada.
 */
object ThemedArtwork {
    /** Para las canciones sin carátula: igualmente se manda la imagen del tema. */
    val PLACEHOLDER: Uri = Uri.parse("freedomplayer://artwork/sin-caratula")

    const val SIZE = 720

    private val night = Color.parseColor("#110C1B")
    private val nightTop = Color.parseColor("#1E1630")
    private val nightDeep = Color.parseColor("#0B0812")
    private val neon = Color.parseColor("#FF3FA4")
    private val ghostPink = Color.parseColor("#F4A6CB")
    private val ghostInk = Color.parseColor("#C2326F")
    private val coverFallback = Color.parseColor("#7A1F55")
    private val windows = intArrayOf(neon, Color.parseColor("#5B57D9"), Color.parseColor("#2F9C82"), ghostPink)

    /** Monta [cover] (o nada) en el tema. Devuelve una imagen cuadrada de [SIZE]. */
    fun compose(cover: Bitmap?, size: Int = SIZE): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val s = size.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Noche, de arriba a abajo.
        paint.shader = LinearGradient(0f, 0f, 0f, s, nightTop, nightDeep, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, paint)
        paint.shader = null

        // El halo de la farola, arriba a la derecha.
        val lampX = s * 0.80f
        val lampY = s * 0.07f
        paint.shader = RadialGradient(lampX, lampY, s * 0.62f, intArrayOf(withAlpha(neon, 120), withAlpha(neon, 34), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, paint)
        paint.shader = null
        paint.color = Color.parseColor("#FFE3F1")
        c.drawCircle(lampX, lampY, s * 0.022f, paint)

        skyline(c, s, paint)

        // La carátula, con las esquinas cortadas y el marco de neón desplazado (como en el reproductor).
        val side = s * 0.60f
        val left = (s - side) / 2f
        val top = s * 0.15f
        val cut = side * 0.08f
        val shift = s * 0.028f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.005f
        paint.color = withAlpha(neon, 170)
        c.drawPath(cutCorners(left + shift, top + shift, side, cut), paint)
        paint.style = Paint.Style.FILL

        val coverPath = cutCorners(left, top, side, cut)
        c.save()
        c.clipPath(coverPath)
        if (cover != null) {
            c.drawBitmap(cover, centerCrop(cover), RectF(left, top, left + side, top + side), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            paint.color = coverFallback
            c.drawRect(left, top, left + side, top + side, paint)
            ghost(c, left + side * 0.25f, top + side * 0.22f, side * 0.5f, paint)
        }
        c.restore()

        // Brillo rosa bajo la carátula y el fantasmita asomando por la esquina.
        sparkle(c, left - s * 0.035f, top + s * 0.02f, s * 0.045f, paint)
        if (cover != null) ghost(c, left + side - s * 0.075f, top - s * 0.075f, s * 0.15f, paint)
        return out
    }

    private fun skyline(c: Canvas, s: Float, paint: Paint) {
        val rnd = Random(7)
        val base = s
        var x = 0f
        val shades = intArrayOf(Color.parseColor("#1A1328"), Color.parseColor("#160F22"), Color.parseColor("#1C1430"), Color.parseColor("#150F21"))
        var i = 0
        while (x < s) {
            val w = s * (0.11f + rnd.nextFloat() * 0.12f)
            val h = s * (0.16f + rnd.nextFloat() * 0.14f)
            paint.color = shades[i % shades.size]
            c.drawRect(x, base - h, x + w + 1f, base, paint)
            var wy = base - h + s * 0.03f
            while (wy < base - s * 0.04f) {
                var wx = x + s * 0.02f
                while (wx < x + w - s * 0.02f) {
                    if (rnd.nextFloat() < 0.22f) {
                        paint.color = withAlpha(windows[rnd.nextInt(windows.size)], 60 + rnd.nextInt(110))
                        c.drawRect(wx, wy, wx + s * 0.012f, wy + s * 0.015f, paint)
                    }
                    wx += s * 0.03f
                }
                wy += s * 0.04f
            }
            x += w
            i++
        }
        paint.color = night
        c.drawRect(0f, base - s * 0.012f, s, base, paint)
    }

    /** Cuadrado con las esquinas de arriba-izquierda y abajo-derecha cortadas. */
    private fun cutCorners(x: Float, y: Float, side: Float, cut: Float) = Path().apply {
        moveTo(x + cut, y)
        lineTo(x + side, y)
        lineTo(x + side, y + side - cut)
        lineTo(x + side - cut, y + side)
        lineTo(x, y + side)
        lineTo(x, y + cut)
        close()
    }

    /** Recorte centrado de la carátula para llenar un cuadrado. */
    private fun centerCrop(b: Bitmap): Rect {
        val side = minOf(b.width, b.height)
        val x = (b.width - side) / 2
        val y = (b.height - side) / 2
        return Rect(x, y, x + side, y + side)
    }

    /** El fantasmita feliz (>‿<) del tema, en una caja de [size] desde ([x], [y]). */
    private fun ghost(c: Canvas, x: Float, y: Float, size: Float, paint: Paint) {
        c.save()
        c.translate(x, y)
        c.scale(size / 64f, size / 64f)
        paint.style = Paint.Style.FILL
        paint.color = ghostPink
        c.drawPath(ghostBody, paint)
        paint.color = withAlpha(Color.WHITE, 90)
        c.drawOval(16f, 12f, 30f, 20f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = ghostInk
        c.drawPath(Path().apply { moveTo(20f, 23f); lineTo(26f, 27f); lineTo(20f, 31f) }, paint)
        c.drawPath(Path().apply { moveTo(44f, 23f); lineTo(38f, 27f); lineTo(44f, 31f) }, paint)
        c.drawPath(Path().apply { moveTo(28f, 33f); quadTo(32f, 37f, 36f, 33f) }, paint)
        paint.style = Paint.Style.FILL
        c.restore()
    }

    private val ghostBody = Path().apply {
        moveTo(32f, 6f)
        cubicTo(18f, 6f, 12f, 17f, 12f, 29f)
        lineTo(12f, 33f)
        cubicTo(8f, 34f, 5f, 37f, 4f, 40f)
        cubicTo(8f, 40f, 11f, 39f, 12f, 38f)
        lineTo(12f, 50f)
        cubicTo(15f, 54f, 18f, 56f, 21f, 52f)
        cubicTo(24f, 57f, 28f, 58f, 32f, 53f)
        cubicTo(36f, 58f, 40f, 57f, 43f, 52f)
        cubicTo(46f, 56f, 49f, 54f, 52f, 50f)
        lineTo(52f, 38f)
        cubicTo(53f, 39f, 56f, 40f, 60f, 40f)
        cubicTo(59f, 37f, 56f, 34f, 52f, 33f)
        lineTo(52f, 29f)
        cubicTo(52f, 17f, 46f, 6f, 32f, 6f)
        close()
    }

    /** La estrella ✦ de cuatro puntas. */
    private fun sparkle(c: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val k = r * 0.18f
        paint.color = neon
        c.drawPath(
            Path().apply {
                moveTo(cx, cy - r); lineTo(cx + k, cy - k); lineTo(cx + r, cy); lineTo(cx + k, cy + k)
                lineTo(cx, cy + r); lineTo(cx - k, cy + k); lineTo(cx - r, cy); lineTo(cx - k, cy - k); close()
            },
            paint,
        )
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

/**
 * Carga las carátulas para la sesión de Media3 (notificación, bloqueo, panel) y las devuelve
 * montadas con [ThemedArtwork]. Lo de leer la imagen lo hace el cargador normal de Media3.
 */
@OptIn(UnstableApi::class)
class ThemedBitmapLoader(private val base: BitmapLoader) : BitmapLoader {
    private val worker: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = base.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        worker.submit<Bitmap> { ThemedArtwork.compose(BitmapFactory.decodeByteArray(data, 0, data.size)) }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri == ThemedArtwork.PLACEHOLDER) return worker.submit<Bitmap> { ThemedArtwork.compose(null) }
        // Si la carátula no se puede leer, la imagen del tema sin ella (mejor que nada).
        val themed = Futures.transform(base.loadBitmap(uri), { ThemedArtwork.compose(it) }, worker)
        return Futures.catching(themed, Throwable::class.java, { ThemedArtwork.compose(null) }, worker)
    }
}
