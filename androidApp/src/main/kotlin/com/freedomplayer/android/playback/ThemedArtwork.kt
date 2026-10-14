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
 * Android 13+ el sistema la pone de fondo de su tarjeta (recortada a lo ancho, por el centro) y
 * saca de ella sus colores. Por eso se le da la carátula montada en el tema:
 *
 * - de fondo, la propia carátula a todo lo ancho, oscurecida con la noche del tema (más a la
 *   izquierda, donde el sistema escribe el título);
 * - el halo de la farola, la ✦ y el callejón abajo;
 * - y la carátula **entera** (sin recortar, aunque no sea cuadrada, como las miniaturas de los
 *   vídeos), con el marco de neón desplazado y el fantasmita asomando por fuera.
 *
 * La carátula va a la derecha y centrada en vertical, dentro de la franja que se ve en la
 * tarjeta ancha; el icono pequeño (el fantasma de arriba a la izquierda) lo pone el sistema.
 */
object ThemedArtwork {
    /** Para las canciones sin carátula: igualmente se manda la imagen del tema. */
    val PLACEHOLDER: Uri = Uri.parse("freedomplayer://artwork/sin-caratula")

    const val SIZE = 720

    /** La carátula ocupa este tanto del lado, para caber entera en la franja de la tarjeta ancha (≈ 1,9 : 1). */
    private const val COVER = 0.42f

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
        val smooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Fondo: la carátula a todo lo ancho (o la noche, sin carátula)…
        if (cover != null) {
            c.drawBitmap(cover, centerCrop(cover), RectF(0f, 0f, s, s), smooth)
        } else {
            paint.shader = LinearGradient(0f, 0f, 0f, s, nightTop, nightDeep, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s, s, paint)
            paint.shader = null
        }
        // …oscurecida con la noche: más a la izquierda, donde va el texto del sistema.
        paint.shader = LinearGradient(0f, 0f, s, 0f, withAlpha(nightDeep, 238), withAlpha(night, 170), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, s, intArrayOf(withAlpha(nightTop, 90), Color.TRANSPARENT, withAlpha(nightDeep, 200)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, paint)
        paint.shader = null

        // El halo de la farola, arriba a la derecha.
        val lampX = s * 0.86f
        val lampY = s * 0.06f
        paint.shader = RadialGradient(lampX, lampY, s * 0.6f, intArrayOf(withAlpha(neon, 110), withAlpha(neon, 30), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, paint)
        paint.shader = null
        paint.color = Color.parseColor("#FFE3F1")
        c.drawCircle(lampX, lampY, s * 0.02f, paint)

        skyline(c, s, paint)

        // La carátula entera, a la derecha y centrada en vertical.
        val box = s * COVER
        val boxLeft = s * 0.95f - box
        val boxTop = (s - box) / 2f
        val art = if (cover != null) fit(cover, boxLeft, boxTop, box) else RectF(boxLeft, boxTop, boxLeft + box, boxTop + box)
        val shift = s * 0.022f
        // Sombra y marco de neón desplazado, detrás.
        paint.color = withAlpha(Color.BLACK, 120)
        c.drawRect(art.left + s * 0.01f, art.top + s * 0.014f, art.right + s * 0.01f, art.bottom + s * 0.014f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.005f
        paint.color = withAlpha(neon, 190)
        c.drawRect(art.left + shift, art.top + shift, art.right + shift, art.bottom + shift, paint)
        paint.style = Paint.Style.FILL
        if (cover != null) {
            c.drawBitmap(cover, null, art, smooth)
        } else {
            paint.color = coverFallback
            c.drawRect(art, paint)
            ghost(c, art.left + art.width() * 0.25f, art.top + art.height() * 0.22f, art.width() * 0.5f, paint)
        }
        // Un filo rosa alrededor, sin tapar nada de la carátula.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.004f
        paint.color = withAlpha(ghostPink, 150)
        c.drawRect(art.left - s * 0.002f, art.top - s * 0.002f, art.right + s * 0.002f, art.bottom + s * 0.002f, paint)
        paint.style = Paint.Style.FILL

        // La ✦ arriba a la izquierda de la carátula y el fantasmita asomando por abajo, fuera de ella.
        sparkle(c, art.left - s * 0.045f, art.top + s * 0.01f, s * 0.035f, paint)
        if (cover != null) ghost(c, art.left - s * 0.13f, art.bottom - s * 0.11f, s * 0.12f, paint)
        return out
    }

    /** Dónde va [b] entera (sin recortar) dentro del cuadrado de lado [side] en ([x], [y]), centrada. */
    private fun fit(b: Bitmap, x: Float, y: Float, side: Float): RectF {
        val k = minOf(side / b.width, side / b.height)
        val w = b.width * k
        val h = b.height * k
        val left = x + (side - w) / 2f
        val top = y + (side - h) / 2f
        return RectF(left, top, left + w, top + h)
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
