package com.freedomplayer.platform

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El QR del PC para vincular el móvil: se lee de vuelta y da el mismo id. */
class QrCodesTest {
    private val id = "MFZWI3D-BONSGYC-YLTMRWG-C43ENR5-QXGZDMM-FZWI3DP-BONSGYY-LTMRWAD"

    @Test
    fun theQrOfTheDeviceIdReadsBackTheSame() {
        val cells = QrCodes.matrix(id)
        val n = cells.size
        assertTrue(n in 21..57 && cells.all { it.size == n }, "cuadrado y de una versión pequeña: $n")
        assertTrue((0 until 7).all { cells[0][it] && cells[6][it] && cells[it][0] && cells[it][6] }, "con su marca de esquina")

        // Pintado como en la pantalla (4 px por cuadro y margen blanco) y leído con ZXing.
        val scale = 4
        val margin = 16
        val size = n * scale + margin * 2
        val pixels = IntArray(size * size) { i ->
            val x = (i % size - margin) / scale
            val y = (i / size - margin) / scale
            val inside = i % size >= margin && i / size >= margin && x < n && y < n
            if (inside && cells[y][x]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        assertEquals(id, QRCodeReader().decode(bitmap).text)
    }
}
